package az.millikart.common.web;

import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Прокси, чьим forwarding-заголовкам верит ClientIp; по умолчанию loopback — nginx на той же машине
// (project_docs/guides/deployment_guide.md). Расширять только при реальном втором прокси: каждый
// адрес в списке вправе назвать любого клиента, и лишний — обход лимитера.
@Component
public class TrustedProxies {

    private static final Logger log = LoggerFactory.getLogger(TrustedProxies.class);

    private final Set<String> addresses;

    public TrustedProxies(@Value("${mp.trusted-proxies:127.0.0.1,::1}") String configured) {
        Set<String> parsed = new LinkedHashSet<>();
        if (configured != null) {
            for (String entry : configured.split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    parsed.add(trimmed);
                }
            }
        }
        this.addresses = Set.copyOf(parsed);
        if (this.addresses.isEmpty()) {
            log.info("mp.trusted-proxies is empty: X-Real-IP and X-Forwarded-For are ignored, "
                    + "the client address is always the peer address");
        } else {
            log.info("Trusted proxies for client-address resolution: {}", parsed);
        }
    }

    // Никогда не null. Пустой набор — заголовкам не верить, адрес берётся у пира: безопасный откат.
    public Set<String> addresses() {
        return addresses;
    }
}
