package az.millikart.auth.scheduler;

import az.millikart.auth.service.RefreshTokenService;
import az.millikart.common.logging.SchedulerRun;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Только cron-триггер для RefreshTokenService.deleteExpired: своей логики не держит (та же форма,
// что у TransactionReconciliationScheduler в pbl), поэтому уборка тестируется без ожидания
// расписания. В тестовом профиле выключается через auth.refresh.cleanup-enabled.
@Component
@ConditionalOnProperty(name = "auth.refresh.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenCleanupScheduler(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @Scheduled(cron = "${auth.refresh.cleanup-cron}")
    public void run() {
        try (var ignored = SchedulerRun.start("token-cleanup")) {
            refreshTokenService.deleteExpired(Instant.now());
        }
    }
}
