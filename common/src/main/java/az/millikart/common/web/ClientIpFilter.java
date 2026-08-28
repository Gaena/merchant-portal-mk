package az.millikart.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Определяет адрес клиента один раз за запрос и только через ClientIp.resolve — единственный
// санкционированный путь, чтобы подделанный forwarding-заголовок не подсадил чужой адрес. Дальше
// адрес доступен в ClientIpHolder (журнал аудита) и в MDC под clientIp для каждой строки лога;
// и холдер, и запись MDC снимаются в finally — потоки контейнера переиспользуются.
@Component
// Порядок задан явно: адрес обязан быть на месте, когда работают security-цепочка и контроллеры,
// а строки, пишущиеся при отказе в запросе, — ровно те, которым он нужнее всего. Порядок
// регистрации по умолчанию поставил бы этот фильтр после security-цепочки.
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClientIpFilter extends OncePerRequestFilter {

    public static final String MDC_CLIENT_IP_KEY = "clientIp";

    private final TrustedProxies trustedProxies;

    public ClientIpFilter(TrustedProxies trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = ClientIp.resolve(request, trustedProxies.addresses());
        ClientIpHolder.set(clientIp);
        if (clientIp != null) {
            MDC.put(MDC_CLIENT_IP_KEY, clientIp);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            ClientIpHolder.clear();
            MDC.remove(MDC_CLIENT_IP_KEY);
        }
    }
}
