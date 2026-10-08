package az.millikart.directory.scheduler;

import az.millikart.common.logging.SchedulerRun;
import az.millikart.directory.service.AuditIntegrityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Ночная проверка цепочки журнала (Р-138, PCI DSS 10.3.4): правка журнала мимо приложения поднимает тревогу —
// ERROR с маркером AUDIT_CHAIN_BROKEN и запись UNRESOLVED, не дожидаясь, пока кто-то нажмёт кнопку.
@Component
@ConditionalOnProperty(name = "mp.audit.integrity.enabled", havingValue = "true", matchIfMissing = true)
public class AuditIntegrityScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditIntegrityScheduler.class);

    private final AuditIntegrityService service;

    public AuditIntegrityScheduler(AuditIntegrityService service) {
        this.service = service;
    }

    @Scheduled(cron = "${mp.audit.integrity.cron:0 30 3 * * *}")
    public void run() {
        try (var ignored = SchedulerRun.start("audit-integrity")) {
            service.verifyScheduled();
        } catch (RuntimeException e) {
            log.error("Audit chain verification failed: {}", e.getMessage(), e);
        }
    }
}
