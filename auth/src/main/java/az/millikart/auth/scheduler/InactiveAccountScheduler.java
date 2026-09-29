package az.millikart.auth.scheduler;

import az.millikart.auth.service.InactiveAccountService;
import az.millikart.common.logging.SchedulerRun;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Только cron-триггер: логика — в InactiveAccountService, тесты зовут её без расписания.
@Component
@ConditionalOnProperty(name = "auth.inactivity.enabled", havingValue = "true", matchIfMissing = true)
public class InactiveAccountScheduler {

    private static final Logger log = LoggerFactory.getLogger(InactiveAccountScheduler.class);

    private final InactiveAccountService service;

    public InactiveAccountScheduler(InactiveAccountService service) {
        this.service = service;
    }

    @Scheduled(cron = "${auth.inactivity.cron}")
    public void run() {
        try (var ignored = SchedulerRun.start("inactive-accounts")) {
            service.blockInactive(Instant.now());
        } catch (RuntimeException e) {
            log.error("Blocking of inactive accounts failed: {}", e.getMessage(), e);
        }
    }
}
