package az.millikart.pbl.scheduler;

import az.millikart.common.logging.SchedulerRun;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.service.StatusChangeAudit;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Уборка статусов: открытие просроченной ссылки отказывает и без неё. В тестах выключен — иначе раз в
// пять минут он переводил бы в EXPIRED фикстуры чужих тестов.
@Component
@ConditionalOnProperty(name = "pbl.link-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentLinkScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentLinkScheduler.class);

    private final PaymentLinkRepository paymentLinkRepository;
    private final StatusChangeAudit statusChanges;

    public PaymentLinkScheduler(PaymentLinkRepository paymentLinkRepository, StatusChangeAudit statusChanges) {
        this.paymentLinkRepository = paymentLinkRepository;
        this.statusChanges = statusChanges;
    }

    // Каждая истёкшая ссылка — запись журнала от system (журнал аудита, этап 2): пишется только то, что UPDATE
    // действительно перевёл, а не все кандидаты выборки.
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void cleanupExpiredLinksAndSessions() {
        try (var ignored = SchedulerRun.start("link-expiry")) {
            Instant now = Instant.now();
            List<PaymentLinkRepository.ExpiringLink> candidates = paymentLinkRepository.findExpiring(now);
            if (candidates.isEmpty()) {
                return;
            }
            Map<UUID, String> companyOf = candidates.stream().collect(Collectors.toMap(
                    PaymentLinkRepository.ExpiringLink::getId,
                    candidate -> candidate.getCompanyId() != null ? candidate.getCompanyId() : "",
                    (first, second) -> first));
            int expiredCount = paymentLinkRepository.expireLinks(companyOf.keySet(), now);
            for (UUID expired : paymentLinkRepository.findExpiredAmong(companyOf.keySet())) {
                String companyId = companyOf.get(expired);
                statusChanges.link(expired, companyId.isEmpty() ? null : companyId, PaymentLinkStatus.ACTIVE,
                        PaymentLinkStatus.EXPIRED, "the expiry time has passed");
            }
            if (expiredCount > 0) {
                log.info("Marked {} payment links EXPIRED", expiredCount);
            }
        }
    }
}
