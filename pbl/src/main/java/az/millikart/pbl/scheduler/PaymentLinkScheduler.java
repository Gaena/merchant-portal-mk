package az.millikart.pbl.scheduler;

import az.millikart.common.logging.SchedulerRun;
import az.millikart.pbl.repository.PaymentLinkRepository;
import java.time.Instant;
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

    public PaymentLinkScheduler(PaymentLinkRepository paymentLinkRepository) {
        this.paymentLinkRepository = paymentLinkRepository;
    }

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void cleanupExpiredLinksAndSessions() {
        try (var ignored = SchedulerRun.start("link-expiry")) {
            int expiredCount = paymentLinkRepository.expireActiveLinksBefore(Instant.now());
            if (expiredCount > 0) {
                log.info("Marked {} payment links EXPIRED", expiredCount);
            }
        }
    }
}
