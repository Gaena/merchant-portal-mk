package az.millikart.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_TRACE_ID_KEY = "traceId";

    // Значение клиента ложится в каждую строку лога, а поля там позиционные: «x] [10.0.0.1] [admin@…»
    // подделало бы адрес и пользователя. Всё, что не короткий id, заменяется своим.
    private static final Pattern ACCEPTED_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = accepted(request.getHeader(TRACE_ID_HEADER));
        if (traceId == null) {
            traceId = accepted(request.getHeader(CORRELATION_ID_HEADER));
        }
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }

        MDC.put(MDC_TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID_KEY);
        }
    }

    private static String accepted(String candidate) {
        return candidate != null && ACCEPTED_TRACE_ID.matcher(candidate).matches() ? candidate : null;
    }
}
