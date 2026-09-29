package az.millikart.auth.security;

import az.millikart.common.exception.TooManyRequestsException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Optional;
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
        if (enabled) {
            log.info("Login rate limit: {} failed attempts per {} per client address", maxFailures, window);
        } else {
            log.warn("Login rate limit is DISABLED (auth.login.rate-limit.enabled=false) — "
                    + "failed login attempts are not capped per client address");
        }
    }

    // Звать первым, до поиска пользователя и BCrypt: проверка после хэширования уже оплатила атаку.
    public void checkAllowed(String clientIp) {
        if (!enabled || clientIp == null) {
            return;
        }
        Integer count = failures.getIfPresent(clientIp);
        if (count == null || count < maxFailures) {
            return;
        }
        Duration retryAfter = remainingWindow(clientIp);
        log.warn("{}: {} failed login attempts from {} — refusing further attempts for {}",
                LOGIN_RATE_LIMITED_MARKER, count, clientIp, retryAfter);
        throw new TooManyRequestsException(MESSAGE, retryAfter);
    }

    // Считается и несуществующий логин: из него состоит перебор. true — ровно раз за окно, на попытке,
    // достигшей лимита: на этом держится одна запись в журнал за окно (P2-14).
    public boolean recordFailure(String clientIp) {
        if (!enabled || clientIp == null) {
            return false;
        }
        int count = failures.asMap().merge(clientIp, 1, Integer::sum);
        if (count == maxFailures) {
            log.warn("{}: address {} reached {} failed login attempts; blocked for {}",
                    LOGIN_RATE_LIMITED_MARKER, clientIp, count, window);
            return true;
        }
        return false;
    }

    // Успешный вход обнуляет счётчик адреса: офис за одним NAT не запирает сам себя.
    public void reset(String clientIp) {
        if (clientIp == null) {
            return;
        }
        failures.invalidate(clientIp);
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
