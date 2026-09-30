package az.millikart.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

// traceId связывает строки одного запроса в логе и отдаётся в X-Trace-Id (deployment_guide, раздел о логах).
// Из MDC он обязан уйти и после сбоя: поток Tomcat переиспользуется, и оставшийся traceId приписал бы
// строки следующего запроса этому.
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void theCallersTraceId_isLoggedAndEchoed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "3f9c2a1b");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals("3f9c2a1b", traceIdSeenInside(request, response));
        assertEquals("3f9c2a1b", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }

    @Test
    void correlationId_isTheFallback() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.CORRELATION_ID_HEADER, "corr-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals("corr-42", traceIdSeenInside(request, response));
        assertEquals("corr-42", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }

    @Test
    void withoutAHeader_eachRequestGetsItsOwnEightCharacterId() throws Exception {
        String first = traceIdSeenInside(new MockHttpServletRequest(), new MockHttpServletResponse());
        String second = traceIdSeenInside(new MockHttpServletRequest(), new MockHttpServletResponse());

        assertTrue(first.matches("[0-9a-f]{8}"), first);
        assertNotEquals(first, second);
    }

    // Поля строки лога позиционные ([traceId] [адрес] [пользователь]): значение со скобками подделало бы адрес
    // и пользователя, и разбор инцидента grep'ом приписал бы попытки чужим. Такое заменяется своим id.
    @ParameterizedTest
    @ValueSource(strings = {"x] [10.0.0.1] [admin@millikart.az", "trace id", "tab\there", "трасса", ""})
    void anUnsafeTraceId_isReplacedWithOurOwn(String unsafe) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, unsafe);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String used = traceIdSeenInside(request, response);

        assertTrue(used.matches("[0-9a-f]{8}"), used);
        assertEquals(used, response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }

    // Длина ограничена: иначе каждая строка лога этого запроса несла бы килобайты от клиента.
    @Test
    void sixtyFourCharactersIsTheLongestAcceptedTraceId() throws Exception {
        MockHttpServletRequest longest = new MockHttpServletRequest();
        longest.addHeader(TraceIdFilter.TRACE_ID_HEADER, "a".repeat(64));
        MockHttpServletRequest tooLong = new MockHttpServletRequest();
        tooLong.addHeader(TraceIdFilter.TRACE_ID_HEADER, "a".repeat(65));

        assertEquals("a".repeat(64), traceIdSeenInside(longest, new MockHttpServletResponse()));
        assertTrue(traceIdSeenInside(tooLong, new MockHttpServletResponse()).matches("[0-9a-f]{8}"));
    }

    @Test
    void anUnsafeTraceId_fallsBackToASafeCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "x] [10.0.0.1]");
        request.addHeader(TraceIdFilter.CORRELATION_ID_HEADER, "corr-42");

        assertEquals("corr-42", traceIdSeenInside(request, new MockHttpServletResponse()));
    }

    @Test
    void theTraceId_leavesTheThreadEvenWhenTheRequestFails() {
        FilterChain failing = (request, response) -> {
            throw new ServletException("boom");
        };

        assertThrows(ServletException.class,
                () -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failing));
        assertNull(MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY));
    }

    private String traceIdSeenInside(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> seen.set(MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY)));
        assertNull(MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY), "the trace id must not outlive the request");
        return seen.get();
    }
}
