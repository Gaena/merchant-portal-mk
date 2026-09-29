package az.millikart.auth.scheduler;

import az.millikart.auth.service.RefreshTokenService;
import az.millikart.common.logging.SchedulerRun;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Только cron-триггер: логика — в RefreshTokenService.deleteExpired, тесты зовут её без расписания.
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
