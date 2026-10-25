package az.millikart.auth.scheduler;

import az.millikart.auth.service.MfaService;
import az.millikart.auth.service.RefreshTokenService;
import az.millikart.common.logging.SchedulerRun;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Только cron-триггер: логика — в RefreshTokenService.deleteExpired и MfaService.deleteExpiredChallenges (Р-141),
// тесты зовут их без расписания.
@Component
@ConditionalOnProperty(name = "auth.refresh.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;
    private final MfaService mfaService;

    public RefreshTokenCleanupScheduler(RefreshTokenService refreshTokenService, MfaService mfaService) {
        this.refreshTokenService = refreshTokenService;
        this.mfaService = mfaService;
    }

    @Scheduled(cron = "${auth.refresh.cleanup-cron}")
    public void run() {
        try (var ignored = SchedulerRun.start("token-cleanup")) {
            Instant now = Instant.now();
            refreshTokenService.deleteExpired(now);
            mfaService.deleteExpiredChallenges(now);
        }
    }
}
