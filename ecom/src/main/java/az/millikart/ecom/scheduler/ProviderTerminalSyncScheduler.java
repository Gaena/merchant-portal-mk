package az.millikart.ecom.scheduler;

import az.millikart.common.logging.SchedulerRun;
import az.millikart.ecom.service.ProviderLoginSyncService;
import az.millikart.ecom.service.ProviderTerminalSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Расписанием, а не при входе администратора: иначе недоступный шлюз не пускал бы в портал никого
// (Р-66, Р-94). Сбой одного слепка другой не останавливает.
@Component
@ConditionalOnProperty(name = "ecom.terminal-sync.enabled", havingValue = "true", matchIfMissing = true)
public class ProviderTerminalSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProviderTerminalSyncScheduler.class);

    private final ProviderTerminalSyncService service;
    private final ProviderLoginSyncService loginSyncService;

    public ProviderTerminalSyncScheduler(ProviderTerminalSyncService service,
                                         ProviderLoginSyncService loginSyncService) {
        this.service = service;
        this.loginSyncService = loginSyncService;
    }

    @Scheduled(cron = "${ecom.terminal-sync.cron:0 */15 * * * *}")
    public void run() {
        try (var ignored = SchedulerRun.start("provider-sync")) {
            try {
                service.sync();
            } catch (RuntimeException e) {
                log.error("Provider terminal sync failed: {}", e.getMessage(), e);
            }
            try {
                loginSyncService.sync();
            } catch (RuntimeException e) {
                log.error("Provider login sync failed: {}", e.getMessage(), e);
            }
        }
    }
}
