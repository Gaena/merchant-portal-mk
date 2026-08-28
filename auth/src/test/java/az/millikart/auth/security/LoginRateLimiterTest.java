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

    @Test
    @DisplayName("8. max-failures attempts pass, the next one is refused")
    void failuresUpToTheLimitPass_theNextIsRefused() {
        FakeTicker ticker = new FakeTicker();
        LoginRateLimiter limiter = limiter(true, ticker);

        for (int i = 0; i < MAX_FAILURES; i++) {
            assertDoesNotThrow(() -> limiter.checkAllowed(IP), "attempt " + i + " must still be allowed");
            limiter.recordFailure(IP);
        }

        TooManyRequestsException refused = assertThrows(TooManyRequestsException.class, () -> limiter.checkAllowed(IP));
        assertTrue(refused.getRetryAfter().compareTo(Duration.ZERO) > 0, "Retry-After must be positive");
        assertTrue(refused.getRetryAfter().compareTo(WINDOW) <= 0, "Retry-After must not exceed the window");
    }

    // Иначе офис за одним NAT-адресом блокирует сам себя к середине утра.
    @Test
    @DisplayName("9. a successful login clears the address")
    void successResetsTheAddress() {
        LoginRateLimiter limiter = limiter(true, new FakeTicker());
        for (int i = 0; i < MAX_FAILURES; i++) {
            limiter.recordFailure(IP);
        }
        assertThrows(TooManyRequestsException.class, () -> limiter.checkAllowed(IP));

        limiter.reset(IP);

        assertDoesNotThrow(() -> limiter.checkAllowed(IP));
        assertEquals(java.util.Optional.empty(), limiter.failuresOf(IP));
    }

    @Test
    @DisplayName("10. the counter is empty again once the window has passed")
    void theWindowExpiresTheCounter() {
        FakeTicker ticker = new FakeTicker();
        LoginRateLimiter limiter = limiter(true, ticker);
        for (int i = 0; i < MAX_FAILURES; i++) {
            limiter.recordFailure(IP);
        }
        assertThrows(TooManyRequestsException.class, () -> limiter.checkAllowed(IP));

        ticker.advance(WINDOW.plusSeconds(1));

        assertEquals(java.util.Optional.empty(), limiter.failuresOf(IP));
        assertDoesNotThrow(() -> limiter.checkAllowed(IP));
    }

    @Test
    @DisplayName("11. addresses are counted independently")
    void addressesAreIndependent() {
        LoginRateLimiter limiter = limiter(true, new FakeTicker());
        for (int i = 0; i < MAX_FAILURES; i++) {
            limiter.recordFailure(IP);
        }

        assertThrows(TooManyRequestsException.class, () -> limiter.checkAllowed(IP));
        assertDoesNotThrow(() -> limiter.checkAllowed("198.51.100.4"));
    }

    // Аварийный выход для локальной отладки — и ничего больше; см. комментарий в yaml.
    @Test
    @DisplayName("12. disabled: nothing is counted and nothing is refused")
    void disabledLimiterRefusesNothing() {
        LoginRateLimiter limiter = limiter(false, new FakeTicker());

        for (int i = 0; i < MAX_FAILURES * 10; i++) {
            limiter.recordFailure(IP);
        }

        assertDoesNotThrow(() -> limiter.checkAllowed(IP));
        assertEquals(java.util.Optional.empty(), limiter.failuresOf(IP));
    }

    @Test
    @DisplayName("a nonsensical configuration is refused at startup, not silently ignored")
    void invalidConfigurationIsRefused() {
        assertThrows(IllegalStateException.class, () -> new LoginRateLimiter(true, 0, WINDOW, new FakeTicker()));
        assertThrows(IllegalStateException.class, () -> new LoginRateLimiter(true, 3, Duration.ZERO, new FakeTicker()));
        assertThrows(IllegalStateException.class, () -> new LoginRateLimiter(true, 3, null, new FakeTicker()));
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
