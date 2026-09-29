package az.millikart.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

// Что фильтр обещает логам. Истёкший токен — штатный поток раз в 15 минут у каждого вошедшего, и он не
// пишется ни ERROR, ни WARN: раньше каждый давал ERROR со стектрейсом. Поддельный — WARN без стектрейса.
// Логин вошедшего стоит в MDC ровно на время запроса: потоки контейнера переиспользуются.
class JwtAuthFilterTest {

    private static final String SECRET = "test-only-jwt-secret-not-used-anywhere-else-0123456789";
    private static final String OTHER_SECRET = "another-test-only-jwt-secret-0123456789-abcdef";
    private static final String SECURE_PATH = "/api/v1/payment-links";

    private final JwtProvider provider = new JwtProvider(SECRET, 60_000);
    private Logger filterLogger;
    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void captureLogs() {
        filterLogger = (Logger) LoggerFactory.getLogger(JwtAuthFilter.class);
        events = new ListAppender<>();
        events.start();
        filterLogger.addAppender(events);
    }

    @AfterEach
    void releaseLogs() {
        filterLogger.detachAppender(events);
    }

    @Test
    void anExpiredToken_isRefusedWithoutAnErrorOrAWarning() throws Exception {
        String expired = new JwtProvider(SECRET, -60_000).generateToken("1", "head@comp1.com", "COMPANY_HEAD", "comp-01");

        MockHttpServletResponse response = run(expired, (request, ignored) -> {
            throw new AssertionError("an expired token must not reach the controller");
        });

        assertEquals(401, response.getStatus());
        assertTrue(atLeast(Level.WARN).isEmpty(), atLeast(Level.WARN).toString());
    }

    @Test
    void aForgedToken_isOneWarningWithoutAStackTrace() throws Exception {
        String forged = new JwtProvider(OTHER_SECRET, 60_000).generateToken("1", "head@comp1.com", "SYSTEM_ADMIN", null);

        MockHttpServletResponse response = run(forged, (request, ignored) -> {
            throw new AssertionError("a forged token must not reach the controller");
        });

        assertEquals(401, response.getStatus());
        List<ILoggingEvent> warnings = atLeast(Level.WARN);
        assertEquals(1, warnings.size(), warnings.toString());
        assertEquals(Level.WARN, warnings.getFirst().getLevel());
        assertNull(warnings.getFirst().getThrowableProxy(), "no stack trace for a refused token");
    }

    @Test
    void theSignedInLogin_isInTheMdcForTheRequestOnly() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        MockHttpServletResponse response = run(provider.generateToken("1", "head@comp1.com", "COMPANY_HEAD", "comp-01"),
                (request, ignored) -> seen.set(MDC.get(JwtAuthFilter.MDC_USER_KEY)));

        assertEquals(200, response.getStatus());
        assertEquals("head@comp1.com", seen.get());
        assertNull(MDC.get(JwtAuthFilter.MDC_USER_KEY), "the login must not outlive the request on this thread");
    }

    private MockHttpServletResponse run(String token, FilterChain chain) throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(provider,
                new SecurityErrorResponder(new ObjectMapper().findAndRegisterModules()), "", false, false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", SECURE_PATH);
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private List<ILoggingEvent> atLeast(Level level) {
        return events.list.stream().filter(event -> event.getLevel().isGreaterOrEqual(level)).toList();
    }
}
