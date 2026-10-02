package az.millikart.auth.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import az.millikart.common.exception.TooManyRequestsException;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// P3-Auth: предел неудачных входов на адрес, на часах, которыми управляет тест. Без Spring —
// лимитер это счётчик и окно, а пакетный конструктор принимает Ticker именно затем, чтобы
// "окно прошло" проверялось без sleep.
class LoginRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_FAILURES = 3;
    private static final String IP = "203.0.113.7";
    private static final String STRANGER = "someone-else@millikart.az";
    private static final String OWNER = "owner@millikart.az";

    @Test
    @DisplayName("8. max-failures attempts pass, the next one is refused")
    void failuresUpToTheLimitPass_theNextIsRefused() {
        FakeTicker ticker = new FakeTicker();
        LoginRateLimiter limiter = limiter(true, ticker);

        for (int i = 0; i < MAX_FAILURES; i++) {
            assertDoesNotThrow(() -> attempt(limiter, IP), "attempt " + i + " must still be allowed");
            limiter.recordFailure(IP, STRANGER);
        }

        TooManyRequestsException refused = assertThrows(TooManyRequestsException.class, () -> attempt(limiter, IP));
        assertTrue(refused.getRetryAfter().compareTo(Duration.ZERO) > 0, "Retry-After must be positive");
        assertTrue(refused.getRetryAfter().compareTo(WINDOW) <= 0, "Retry-After must not exceed the window");
    }

    // Успех снимает с адреса только неудачи своего логина (Р-117): опечатки сотрудника офиса за одним NAT
    // не запирают остальных, а перебор чужих логинов свой вход больше не обнуляет (RATE-LIMIT-RESET).
    @Test
    @DisplayName("9. a successful login clears only its own failures from the address")
    void successClearsOnlyItsOwnFailures() {
        LoginRateLimiter limiter = limiter(true, new FakeTicker());
        limiter.recordFailure(IP, OWNER);
        for (int i = 0; i < MAX_FAILURES - 1; i++) {
            limiter.recordFailure(IP, STRANGER);
        }
        assertThrows(TooManyRequestsException.class, () -> attempt(limiter, IP));

        limiter.clearFailuresOf(IP, OWNER);

        assertDoesNotThrow(() -> attempt(limiter, IP));
        assertEquals(java.util.Optional.of(MAX_FAILURES - 1), limiter.failuresOf(IP));

        limiter.clearFailuresOf(IP, OWNER);
        assertEquals(java.util.Optional.of(MAX_FAILURES - 1), limiter.failuresOf(IP),
                "a login without failures of its own clears nothing");
        limiter.clearFailuresOf(IP, STRANGER);
        assertEquals(java.util.Optional.empty(), limiter.failuresOf(IP));
    }

    @Test
    @DisplayName("10. the counter is empty again once the window has passed")
    void theWindowExpiresTheCounter() {
        FakeTicker ticker = new FakeTicker();
        LoginRateLimiter limiter = limiter(true, ticker);
        for (int i = 0; i < MAX_FAILURES; i++) {
            limiter.recordFailure(IP, STRANGER);
        }
        assertThrows(TooManyRequestsException.class, () -> attempt(limiter, IP));

        ticker.advance(WINDOW.plusSeconds(1));

        assertEquals(java.util.Optional.empty(), limiter.failuresOf(IP));
        assertDoesNotThrow(() -> attempt(limiter, IP));
    }

    @Test
    @DisplayName("11. addresses are counted independently")
    void addressesAreIndependent() {
        LoginRateLimiter limiter = limiter(true, new FakeTicker());
        for (int i = 0; i < MAX_FAILURES; i++) {
            limiter.recordFailure(IP, STRANGER);
        }

        assertThrows(TooManyRequestsException.class, () -> attempt(limiter, IP));
        assertDoesNotThrow(() -> attempt(limiter, "198.51.100.4"));
    }

    // Аварийный выход для локальной отладки — и ничего больше; см. комментарий в yaml.
    @Test
    @DisplayName("12. disabled: nothing is counted and nothing is refused")
    void disabledLimiterRefusesNothing() {
        LoginRateLimiter limiter = limiter(false, new FakeTicker());

        for (int i = 0; i < MAX_FAILURES * 10; i++) {
            limiter.recordFailure(IP, STRANGER);
        }

        assertDoesNotThrow(() -> attempt(limiter, IP));
        assertEquals(java.util.Optional.empty(), limiter.failuresOf(IP));
        limiter.begin(IP, OWNER);
        assertDoesNotThrow(() -> limiter.begin(IP, OWNER).close());
    }

    @Test
    @DisplayName("a nonsensical configuration is refused at startup, not silently ignored")
    void invalidConfigurationIsRefused() {
        assertThrows(IllegalStateException.class, () -> new LoginRateLimiter(true, 0, WINDOW, new FakeTicker()));
        assertThrows(IllegalStateException.class, () -> new LoginRateLimiter(true, 3, Duration.ZERO, new FakeTicker()));
        assertThrows(IllegalStateException.class, () -> new LoginRateLimiter(true, 3, null, new FakeTicker()));
    }

    // LOGIN-POOL (Р-118): проверка читала счётчик, и залп параллельных попыток проходил её целиком — каждая
    // доходила до BCrypt. Теперь идущая попытка держит место в лимите адреса до своего конца.
    @Test
    @DisplayName("13. running attempts take up the address allowance until they end")
    void runningAttemptsReserveTheAllowance() {
        LoginRateLimiter limiter = limiter(true, new FakeTicker());
        java.util.List<LoginRateLimiter.Attempt> running = new java.util.ArrayList<>();
        for (int i = 0; i < MAX_FAILURES; i++) {
            running.add(limiter.begin(IP, "login-" + i + "@millikart.az"));
        }

        TooManyRequestsException refused = assertThrows(TooManyRequestsException.class,
                () -> limiter.begin(IP, "one-more@millikart.az"));
        assertEquals(Duration.ofSeconds(1), refused.getRetryAfter());
        assertEquals(java.util.Optional.empty(), limiter.failuresOf(IP), "a reservation is not a failure");

        running.getFirst().close();
        assertDoesNotThrow(() -> limiter.begin(IP, "one-more@millikart.az").close());
    }

    // LOGIN-POOL (Р-118): вторая попытка в тот же логин ждала первую на FOR UPDATE, держа соединение пула, и
    // залп в один логин клал вход всем. Пока идёт первая, вторая получает отказ сразу — с любого адреса.
    @Test
    @DisplayName("14. a login with an attempt running refuses a second one at once")
    void aSecondAttemptOnTheSameLoginIsRefusedWhileTheFirstRuns() {
        LoginRateLimiter limiter = limiter(true, new FakeTicker());
        LoginRateLimiter.Attempt first = limiter.begin(IP, OWNER);

        assertThrows(TooManyRequestsException.class, () -> limiter.begin("198.51.100.9", OWNER));

        first.close();
        first.close();
        assertDoesNotThrow(() -> limiter.begin("198.51.100.9", OWNER).close());
        assertDoesNotThrow(() -> limiter.begin(IP, STRANGER).close());
    }

    private static void attempt(LoginRateLimiter limiter, String clientIp) {
        limiter.begin(clientIp, STRANGER).close();
    }

    private static LoginRateLimiter limiter(boolean enabled, Ticker ticker) {
        return new LoginRateLimiter(enabled, MAX_FAILURES, WINDOW, ticker);
    }

    // Caffeine читает время через Ticker; этот двигается только когда скажет тест.
    private static final class FakeTicker implements Ticker {

        private long nanos;

        @Override
        public long read() {
            return nanos;
        }

        void advance(Duration by) {
            nanos += by.toNanos();
        }
    }
}
