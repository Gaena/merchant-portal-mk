package az.millikart.common.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Пишет отложенные записи журнала (AuditOutbox), когда запрос отработал и его транзакции закрыты:
// соединение из пула к этому моменту отдано. Каждая запись сама ловит и репортит свою ошибку.
@Component
public class AuditOutboxFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AuditOutbox.open();
        try {
            filterChain.doFilter(request, response);
        } finally {
            for (Runnable write : AuditOutbox.close()) {
                write.run();
            }
        }
    }
}
