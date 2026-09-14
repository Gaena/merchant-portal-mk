package az.millikart.directory.scheduler;

import az.millikart.directory.service.TerminalStatusReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Сверяет статусы терминалов со слепком провайдера.
 *
 * Идёт следом за синхронизацией в `ecom` и с тем же периодом: слепок обновляется там, решение
 * о наших терминалах принимается здесь, где смена статуса уже умеет приостанавливать и
 * восстанавливать платёжные ссылки и писать в журнал.
 *
 * Выключается флагом целиком — там, где `ecom` не развёрнут, сверять не с чем, и задача только
 * зря будит базу. Впрочем, и без флага она безвредна: пустой слепок ничего не меняет.
 */
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
        try {
            service.reconcile();
        } catch (RuntimeException e) {
            // Необработанное исключение остановило бы расписание целиком, и следующего прохода
            // не случилось бы никогда.
            log.error("Terminal status reconciliation failed: {}", e.getMessage(), e);
        }
    }
}
