package az.millikart.auth.security;

import az.millikart.common.exception.TooManyRequestsException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Лимит неудачных входов на адрес, в дополнение к локауту аккаунта (Р-28): без него любой, зная
// почту мерчанта, выключает его на полчаса. Счётчики в памяти намеренно (Р-27): строка в базу на
// каждую неудачу сделала бы защиту усилителем нагрузки.
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    // Маркер мониторинга: адрес исчерпал попытки. Поток со многих адресов — распределённый перебор,
    // который лимитер не ловит.
    static final String LOGIN_RATE_LIMITED_MARKER = "LOGIN_RATE_LIMITED";

    // Сообщает только факт отказа: ни счётчиков, ни порогов.
    private static final String MESSAGE = "Too many login attempts. Please try again later.";

    // Потолок отслеживаемых адресов. При переполнении Caffeine вытесняет давние записи — отказ в
    // открытую: вытесненный атакующий получает свежий запас попыток.
    private static final int MAX_TRACKED_ADDRESSES = 100_000;

    private final boolean enabled;
    private final int maxFailures;
    private final Duration window;
    private final Cache<String, Integer> failures;
    // Доля каждого логина в счётчике адреса: успех снимает только её (RATE-LIMIT-RESET, Р-117).
    private final Cache<AddressLogin, Integer> failuresByLogin;

    private record AddressLogin(String clientIp, String login) {
    }

    // Попытки, идущие прямо сейчас: сколько с адреса и какие логины. Под замком лимитера — проверка и
    // резерв одним шагом; внутри только память.
    private final Map<String, Integer> inFlightByAddress = new HashMap<>();
    private final Set<String> inFlightLogins = new HashSet<>();

    @Autowired // второй конструктор существует для тестов; Spring обязан брать этот
    public LoginRateLimiter(@Value("${auth.login.rate-limit.enabled}") boolean enabled,
                            @Value("${auth.login.rate-limit.max-failures}") int maxFailures,
                            @Value("${auth.login.rate-limit.window}") Duration window) {
        this(enabled, maxFailures, window, Ticker.systemTicker());
    }

    // Для тестов: на тикере вызывающего тест перешагивает окно.
    LoginRateLimiter(boolean enabled, int maxFailures, Duration window, Ticker ticker) {
        if (maxFailures < 1) {
            throw new IllegalStateException("auth.login.rate-limit.max-failures must be at least 1, got " + maxFailures);
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalStateException("auth.login.rate-limit.window must be a positive duration, got " + window);
        }
        this.enabled = enabled;
        this.maxFailures = maxFailures;
        this.window = window;
        this.failures = Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(MAX_TRACKED_ADDRESSES)
                .ticker(ticker)
                .build();
        // Вытесненная доля не снимется успехом — адрес лишь дольше остаётся под счётом, а не наоборот.
        this.failuresByLogin = Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(MAX_TRACKED_ADDRESSES)
                .ticker(ticker)
                .build();
        if (enabled) {
            log.info("Login rate limit: {} failed attempts per {} per client address", maxFailures, window);
        } else {
            log.warn("Login rate limit is DISABLED (auth.login.rate-limit.enabled=false) — "
                    + "failed login attempts are not capped per client address");
        }
    }

    // Звать первым, до поиска пользователя и BCrypt, и закрывать после проверки пароля. Резерв, а не проверка:
    // залп параллельных попыток видел один и тот же счётчик и проходил целиком; второй вход в логин, пока идёт
    // первый, ждал бы его на FOR UPDATE, держа соединение пула (LOGIN-POOL, Р-118). Отказ по логину одинаков
    // для существующего и несуществующего: учётку он не выдаёт.
    public Attempt begin(String clientIp, String login) {
        if (!enabled) {
            return new Attempt(null, null, null);
        }
        synchronized (this) {
            if (login != null && inFlightLogins.contains(login)) {
                log.info("Refused a login attempt for {}: another attempt for this login is in progress", login);
                throw new TooManyRequestsException(MESSAGE, Duration.ofSeconds(1));
            }
            if (clientIp != null) {
                int failed = Optional.ofNullable(failures.getIfPresent(clientIp)).orElse(0);
                int inFlight = inFlightByAddress.getOrDefault(clientIp, 0);
                if (failed + inFlight >= maxFailures) {
                    Duration retryAfter = failed >= maxFailures ? remainingWindow(clientIp) : Duration.ofSeconds(1);
                    log.warn("{}: {} failed and {} running login attempts from {} — refusing further attempts for {}",
                            LOGIN_RATE_LIMITED_MARKER, failed, inFlight, clientIp, retryAfter);
                    throw new TooManyRequestsException(MESSAGE, retryAfter);
                }
                inFlightByAddress.merge(clientIp, 1, Integer::sum);
            }
            if (login != null) {
                inFlightLogins.add(login);
            }
        }
        return new Attempt(this, clientIp, login);
    }

    private synchronized void end(String clientIp, String login) {
        if (clientIp != null) {
            inFlightByAddress.computeIfPresent(clientIp, (address, count) -> count > 1 ? count - 1 : null);
        }
        if (login != null) {
            inFlightLogins.remove(login);
        }
    }

    // Место в лимите на время одной попытки; закрывается и при отказе, и при исключении.
    public static final class Attempt implements AutoCloseable {

        private final LoginRateLimiter limiter;
        private final String clientIp;
        private final String login;
        private boolean closed;

        private Attempt(LoginRateLimiter limiter, String clientIp, String login) {
            this.limiter = limiter;
            this.clientIp = clientIp;
            this.login = login;
        }

        @Override
        public void close() {
            if (!closed && limiter != null) {
                closed = true;
                limiter.end(clientIp, login);
            }
        }
    }

    // Считается и несуществующий логин: из него состоит перебор. true — ровно раз за окно, на попытке,
    // достигшей лимита: на этом держится одна запись в журнал за окно (P2-14).
    public boolean recordFailure(String clientIp, String login) {
        if (!enabled || clientIp == null) {
            return false;
        }
        failuresByLogin.asMap().merge(new AddressLogin(clientIp, login), 1, Integer::sum);
        int count = failures.asMap().merge(clientIp, 1, Integer::sum);
        if (count == maxFailures) {
            log.warn("{}: address {} reached {} failed login attempts; blocked for {}",
                    LOGIN_RATE_LIMITED_MARKER, clientIp, count, window);
            return true;
        }
        return false;
    }

    // Успешный вход снимает с адреса только неудачи своего логина: опечатки сотрудника не запирают офис за
    // одним NAT, а чужие логины остаются в счёте — иначе свой вход каждые девять попыток обнулял бы перебор
    // (RATE-LIMIT-RESET, Р-117). Снятие — запись: окно оставшихся отсчитывается заново, блок только длиннее.
    public void clearFailuresOf(String clientIp, String login) {
        if (clientIp == null) {
            return;
        }
        Integer own = failuresByLogin.asMap().remove(new AddressLogin(clientIp, login));
        if (own != null) {
            failures.asMap().computeIfPresent(clientIp, (address, total) -> total > own ? total - own : null);
        }
    }

    // Возраст записи — с последней засчитанной неудачи: отбитые попытки не считаются и блокировку
    // не продлевают.
    private Duration remainingWindow(String clientIp) {
        Duration left = failures.policy().expireAfterWrite()
                .flatMap(expiration -> expiration.ageOf(clientIp))
                .map(window::minus)
                .orElse(window);
        return left.isNegative() || left.isZero() ? Duration.ofSeconds(1) : left;
    }

    // Для тестов.
    Optional<Integer> failuresOf(String clientIp) {
        return Optional.ofNullable(failures.getIfPresent(clientIp));
    }
}
