package az.millikart.auth.service;

import az.millikart.auth.domain.User;
import az.millikart.auth.repository.UserRepository;
import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// PCI DSS 8.2.6 (Р-101): учётка без активности дольше max-idle блокируется. Администратор не
// исключение: единственного заблокированного возвращают в базе (project_docs/guides/deployment_guide.md §20.3).
@Service
public class InactiveAccountService {

    private static final Logger log = LoggerFactory.getLogger(InactiveAccountService.class);

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_BLOCKED = "BLOCKED";
    private static final String SYSTEM_ACTOR = "system";

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration maxIdle;

    public InactiveAccountService(UserRepository userRepository,
                                  RefreshTokenService refreshTokenService,
                                  ApplicationEventPublisher eventPublisher,
                                  @Value("${auth.inactivity.max-idle}") Duration maxIdle) {
        if (maxIdle == null || maxIdle.isZero() || maxIdle.isNegative()) {
            throw new IllegalStateException("auth.inactivity.max-idle must be a positive duration, got " + maxIdle);
        }
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.eventPublisher = eventPublisher;
        this.maxIdle = maxIdle;
    }

    @Transactional
    public int blockInactive(Instant now) {
        List<User> idle = userRepository.findByStatusAndLastActivityAtBefore(STATUS_ACTIVE, now.minus(maxIdle));
        for (User user : idle) {
            Instant lastActivity = user.getLastActivityAt();
            user.setStatus(STATUS_BLOCKED);
            refreshTokenService.revokeAllForUser(user.getId(), now);
            eventPublisher.publishEvent(AuditEvent.of(AuditEntity.USER, user.getId().toString(), AuditAction.BLOCK,
                    SYSTEM_ACTOR, user.getCompanyId(),
                    "Account " + user.getUsername() + " blocked: no activity for more than " + maxIdle.toDays()
                            + " days (PCI DSS 8.2.6), last activity " + lastActivity));
        }
        userRepository.saveAll(idle);
        if (!idle.isEmpty()) {
            log.info("Blocked {} account(s) with no activity for more than {} days", idle.size(), maxIdle.toDays());
        }
        return idle.size();
    }
}
