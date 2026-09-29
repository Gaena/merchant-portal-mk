package az.millikart.pbl.scheduler;

import az.millikart.common.logging.SchedulerRun;
import az.millikart.pbl.service.TransactionReconciliationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pbl.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionReconciliationScheduler {

    private final TransactionReconciliationService reconciliationService;

    public TransactionReconciliationScheduler(TransactionReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(cron = "${pbl.reconciliation.cron}")
    public void run() {
        try (var ignored = SchedulerRun.start("tx-reconcile")) {
            reconciliationService.reconcilePendingTransactions();
        }
    }
}
