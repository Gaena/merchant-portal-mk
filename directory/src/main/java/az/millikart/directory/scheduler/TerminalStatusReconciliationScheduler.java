package az.millikart.directory.scheduler;

import az.millikart.common.logging.SchedulerRun;
import az.millikart.directory.service.TerminalStatusReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Слепок обновляет синхронизация ecom с тем же периодом, а решение о наших терминалах — здесь: смена
// статуса в directory умеет приостанавливать ссылки и писать в журнал.
@Component
@ConditionalOnProperty(name = "directory.terminal-reconciliation.enabled",
        havingValue = "true", matchIfMissing = true)
public class TerminalStatusReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(TerminalStatusReconciliationScheduler.class);

    private final TerminalStatusReconciliationService service;

    public TerminalStatusReconciliationScheduler(TerminalStatusReconciliationService service) {
        this.service = service;
    }

    @Scheduled(cron = "${directory.terminal-reconciliation.cron:0 */15 * * * *}")
    public void run() {
        try (var ignored = SchedulerRun.start("terminal-reconcile")) {
            service.reconcile();
        } catch (RuntimeException e) {
            log.error("Terminal status reconciliation failed: {}", e.getMessage(), e);
        }
    }
}
