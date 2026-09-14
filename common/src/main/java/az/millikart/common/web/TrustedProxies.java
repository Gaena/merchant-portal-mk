package az.millikart.common.web;

import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Прокси, чьим forwarding-заголовкам верит ClientIp: mp.trusted-proxies из TRUSTED_PROXIES, по
// умолчанию 127.0.0.1,::1 — то есть всё наше развёртывание, один nginx на той же машине через
// loopback (project_docs/deployment_guide.md). Расширять список только когда второй прокси реально появился:
// каждый адрес в нём вправе назвать любого клиента, а список «на всякий случай» — обход лимитера.
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

    // Никогда не null. Пустой набор означает «forwarding-заголовкам не верить», адрес всегда
    // берётся у пира: это правильная настройка для сервиса, перед которым ничего не стоит, и
    // безопасный откат, а не авария — адреса просто становятся менее точными.
    public Set<String> addresses() {
        return addresses;
    }
}
