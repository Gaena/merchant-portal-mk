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

// Лимит неудачных входов на адрес. Локаут аккаунта (6 неудач, 30 минут, PCI-DSS 8.3.4, Р-28) —
// отдельный механизм, и этим лимитом он не заменяется: без лимита на источник любой, зная почту
// мерчанта, выключает его на полчаса шестью неудачами. Счётчики намеренно в памяти (Caffeine,
// Р-27): строка в базу на каждую неудачу сделала бы защиту от перебора усилителем нагрузки.
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    // Маркер мониторинга: адрес исчерпал попытки входа. Одна строка — опечатка в пароле, поток с
    // одного адреса — перебор, поток сразу со многих — распределённый, который лимитер не ловит.
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

    // Тот же лимитер на тикере вызывающего — так юнит-тест перешагивает окно.
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

    // Звать ПЕРВЫМ — до поиска пользователя и до BCrypt: смысл лимита в том, что дорогая работа не
    // делается, а проверка после 100 мс хэширования уже оплатила ту атаку, которую должна была
    // предотвратить. Бросает TooManyRequestsException с остатком окна, наружу это HTTP 429.
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

    // Считается каждая неудача, включая несуществующий логин: из него и состоит перебор логинов, а
    // неучтённый даёт прощупывать базу даром. true возвращается ровно раз за окно — на попытке,
    // достигшей лимита; на этом держится «одна запись в журнал за окно, а не на каждую попытку»
    // (P2-14): остальные попытки отбивает checkAllowed, не доходя сюда.
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

    // Успешный вход обнуляет счётчик адреса: офис за одним NAT не запирает сам себя, пока люди
    // входят. Копится только серия неудач подряд — а это и есть перебор.
    public void reset(String clientIp) {
        if (clientIp == null) {
            return;
        }
        failures.invalidate(clientIp);
    }

    // Возраст записи — время с последней ЗАСЧИТАННОЙ неудачи. Пока адрес отбивается, попытки не
    // считаются, поэтому долбёжка не продлевает блокировку и не сокращает её.
    private Duration remainingWindow(String clientIp) {
        Duration left = failures.policy().expireAfterWrite()
                .flatMap(expiration -> expiration.ageOf(clientIp))
                .map(window::minus)
                .orElse(window);
        return left.isNegative() || left.isZero() ? Duration.ofSeconds(1) : left;
    }

    // Открыто тестам: сколько неудач числится за адресом прямо сейчас.
    Optional<Integer> failuresOf(String clientIp) {
        return Optional.ofNullable(failures.getIfPresent(clientIp));
    }
}
