package az.millikart.pbl.scheduler;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TransactionRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentLinkScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentLinkScheduler.class);

    private final PaymentLinkRepository paymentLinkRepository;
    private final TransactionRepository transactionRepository;

    public PaymentLinkScheduler(PaymentLinkRepository paymentLinkRepository,
                                TransactionRepository transactionRepository) {
        this.paymentLinkRepository = paymentLinkRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Periodically cleans up expired links every 5 minutes.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void cleanupExpiredLinksAndSessions() {
        log.debug("Running background cleanup for expired payment links...");

        int expiredCount = paymentLinkRepository.expireActiveLinksBefore(Instant.now());

        if (expiredCount > 0) {
            log.info("Background cleanup: Marked {} expired payment links as EXPIRED.", expiredCount);
        }
    }
}
