package az.millikart.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

// Единственное опасное свойство фильтра — уборка: потоки сервлетов переиспользуются, и holder
// или запись MDC, пережившие запрос, достанутся следующему запросу на том же потоке. Поэтому
// каждый тест здесь проверяет состояние после цепочки, а не только во время неё.
class ClientIpFilterTest {

    private final ClientIpFilter filter = new ClientIpFilter(new TrustedProxies("127.0.0.1,::1"));

    @AfterEach
    void cleanUpThread() {
        ClientIpHolder.clear();
        MDC.remove(ClientIpFilter.MDC_CLIENT_IP_KEY);
    }

    @Test
    void holderAndMdc_filledDuringRequest_clearedAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.7");

        AtomicReference<String> holderInsideChain = new AtomicReference<>();
        AtomicReference<String> mdcInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            holderInsideChain.set(ClientIpHolder.get());
            mdcInsideChain.set(MDC.get(ClientIpFilter.MDC_CLIENT_IP_KEY));
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals("198.51.100.7", holderInsideChain.get());
        assertEquals("198.51.100.7", mdcInsideChain.get());
        assertNull(ClientIpHolder.get(), "holder must be cleared after the request");
        assertNull(MDC.get(ClientIpFilter.MDC_CLIENT_IP_KEY), "MDC must be cleared after the request");
    }

    @Test
    void headerFromUntrustedPeer_isIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Real-IP", "198.51.100.7");

        AtomicReference<String> holderInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> holderInsideChain.set(ClientIpHolder.get());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals("203.0.113.9", holderInsideChain.get());
    }

    @Test
    void holderAndMdc_clearedEvenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        FilterChain chain = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThrows(ServletException.class,
                () -> filter.doFilter(request, new MockHttpServletResponse(), chain));

        assertNull(ClientIpHolder.get(), "holder must be cleared even when the chain throws");
        assertNull(MDC.get(ClientIpFilter.MDC_CLIENT_IP_KEY), "MDC must be cleared even when the chain throws");
    }
}
