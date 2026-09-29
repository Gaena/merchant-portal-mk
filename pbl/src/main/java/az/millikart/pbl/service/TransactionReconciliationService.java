package az.millikart.pbl.service;

import az.millikart.pbl.domain.Transaction;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.pbl.repository.TransactionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Единственный путь зависшей PENDING к финалу (P1-3): колбэка нет, страница возврата спрашивает
// эквайера один раз. AUTHORIZED не трогать: холд DMS в ожидании списания — законное состояние покоя.
@Service
public class TransactionReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(TransactionReconciliationService.class);

    private final TransactionRepository transactionRepository;
    private final PaymentLinkService paymentLinkService;
    private final Duration minAge;
    private final Duration maxAge;
    private final Duration giveUpAge;
    private final int batchSize;

    public TransactionReconciliationService(TransactionRepository transactionRepository,
                                            PaymentLinkService paymentLinkService,
                                            @Value("${pbl.reconciliation.min-age}") Duration minAge,
                                            @Value("${pbl.reconciliation.max-age}") Duration maxAge,
                                            @Value("${pbl.reconciliation.give-up-age}") Duration giveUpAge,
                                            @Value("${pbl.reconciliation.batch-size}") int batchSize) {
        this.transactionRepository = transactionRepository;
        this.paymentLinkService = paymentLinkService;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.giveUpAge = giveUpAge;
        this.batchSize = batchSize;
    }

    // Верхняя граница give-up-age обязательна (P1-8a): PENDING с UNKNOWN или SETTLED_OTHER в FAILED не
    // уходит (Р-20), и без неё такие строки, как самые старые, забьют каждый пакет. Старше give-up-age —
    // только ручной разбор.
    @Transactional(readOnly = true)
    public int reconcilePendingTransactions() {
        Instant now = Instant.now();
        Instant createdBefore = now.minus(minAge);
        Instant createdAfter = now.minus(giveUpAge);
        List<Transaction> stale = transactionRepository.findByStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
                TransactionStatus.PENDING, createdAfter, createdBefore, PageRequest.of(0, batchSize));

        if (stale.isEmpty()) {
            log.debug("Reconciliation: no PENDING transactions between {} and {} old", minAge, giveUpAge);
            logGivenUp(createdAfter);
            return 0;
        }

        List<UUID> batch = stale.stream().map(Transaction::getId).toList();
        log.info("Reconciliation: picked up {} PENDING transaction(s) older than {}", batch.size(), minAge);

        for (UUID transactionId : batch) {
            try {
                // Своя транзакция на запись (REQUIRES_NEW): сбой на одной не роняет пакет.
                paymentLinkService.reconcileOne(transactionId, maxAge);
            } catch (RuntimeException e) {
                log.warn("Reconciliation of transaction {} failed; continuing with the rest of the batch",
                        transactionId, e);
            }
        }

        long stillPending = transactionRepository.countByIdInAndStatus(batch, TransactionStatus.PENDING);
        long givenUp = countGivenUp(createdAfter);
        log.info("Reconciliation finished: processed={}, settled={}, left PENDING={}, PENDING older than give-up-age={} "
                        + "(batchSize={}, minAge={}, maxAge={}, giveUpAge={})",
                batch.size(), batch.size() - stillPending, stillPending, givenUp, batchSize, minAge, maxAge, giveUpAge);
        return batch.size();
    }

    private long countGivenUp(Instant createdAfter) {
        return transactionRepository.countByStatusAndCreatedAtBefore(TransactionStatus.PENDING, createdAfter);
    }

    // Даже холостой проход говорит, сколько строк автоматика бросила: их разбирают руками. Повтор на
    // каждом проходе — намеренное напоминание, исключение из Р-98 (Р-106).
    private void logGivenUp(Instant createdAfter) {
        long givenUp = countGivenUp(createdAfter);
        if (givenUp > 0) {
            log.warn("Reconciliation: {} PENDING transaction(s) are older than give-up-age {} and are no longer "
                    + "polled; they need a manual review", givenUp, giveUpAge);
        }
    }
}
