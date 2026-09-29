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

// Адрес клиента — один раз за запрос, только через ClientIp.resolve; дальше он в ClientIpHolder и в
// MDC. Оба снимаются в finally: потоки контейнера переиспользуются.
@Component
// Первым: адрес нужен security-цепочке и строкам отказов, а по умолчанию фильтр встал бы после неё.
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
