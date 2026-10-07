package az.millikart.auth.service;

import az.millikart.auth.domain.RefreshToken;
import az.millikart.auth.domain.User;
import az.millikart.auth.dto.ChangePasswordRequest;
import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.dto.LoginResponse;
import az.millikart.auth.dto.LogoutRequest;
import az.millikart.auth.dto.RefreshRequest;
import az.millikart.auth.repository.UserRepository;
import az.millikart.auth.security.LoginRateLimiter;
import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditEvent;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.UnauthorizedException;
import az.millikart.common.security.JwtProvider;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // Маркер мониторинга: ротированный refresh-токен предъявлен вне грейса — копия есть у кого-то ещё.
    static final String REFRESH_TOKEN_REUSE_MARKER = "REFRESH_TOKEN_REUSE";

    // Один текст на любой отказ: клиент не должен узнать, ПОЧЕМУ токен отвергнут.
    private static final String INVALID_REFRESH_TOKEN = "Invalid refresh token";

    private static final String STATUS_ACTIVE = "ACTIVE";

    // Один ответ на «нет пользователя» и «неверный пароль»: иначе эндпоинт перечисляет аккаунты.
    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    // Говорится только тому, кто доказал пароль, — поэтому конкретно.
    private static final String ACCOUNT_LOCKED_PREFIX = "Account is locked due to multiple failed login attempts. ";

    // Статус не называется намеренно: «заблокирован» или «удалён» — сведения для администратора.
    private static final String ACCOUNT_NOT_ACTIVE = "Account is not active. Please contact your administrator.";

    // Хэш случайной строки, которую никто не знает: на несуществующем логине matches тратит столько же, сколько
    // на настоящем, иначе неизвестный логин выдают часы. Считается при старте тем же кодировщиком, поэтому
    // стоимость всегда совпадает с настоящими хэшами — константа разошлась бы с mp.security.bcrypt-strength.
    private final String absentUserPasswordHash;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiter rateLimiter;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordHistoryService passwordHistory;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       RefreshTokenService refreshTokenService,
                       LoginRateLimiter rateLimiter,
                       AuditLogService auditLogService,
                       ApplicationEventPublisher eventPublisher,
                       PasswordHistoryService passwordHistory) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiter = rateLimiter;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.passwordHistory = passwordHistory;
        this.absentUserPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request, String clientIp) {
        Instant now = Instant.now();
        User user = authenticate(request.username(), request.password(), clientIp, now);

        // Пароль верен, но задан не владельцем (PCI DSS 8.3.5, Р-100): сессии нет, пока он его не сменит.
        if (user.isPasswordChangeRequired()) {
            auditLogService.logDenied(AuditEntity.AUTH, user.getUsername(), AuditAction.LOGIN, user.getUsername(),
                    user.getCompanyId(), "Login held: the password must be changed first");
            log.info("Login held for user ID {}: the password must be changed first", user.getId());
            return LoginResponse.passwordChangeRequired(user.getRole());
        }
        return startSession(user, now);
    }

    // Смена пароля без сессии: вход по текущему паролю (лимит, локаут, статус), затем новый пароль и
    // сессия (Р-100).
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse changePassword(ChangePasswordRequest request, String clientIp) {
        Instant now = Instant.now();
        User user = authenticate(request.username(), request.currentPassword(), clientIp, now);
        // Не повторяет ни один из четырёх последних (PCI DSS 8.3.7, Р-102); текущий уходит в историю.
        passwordHistory.requireNotRecent(user, request.newPassword());
        passwordHistory.rememberCurrent(user, now);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangeRequired(false);
        userRepository.save(user);
        int revoked = refreshTokenService.revokeAllForUser(user.getId(), now);
        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.USER, user.getId().toString(), AuditAction.PASSWORD_CHANGE,
                user.getUsername(), user.getCompanyId(), "Password changed by its owner at sign-in"));
        log.info("User ID {} changed the password at sign-in: {} refresh token(s) revoked", user.getId(), revoked);
        return startSession(user, now);
    }

    // Порядок проверок — свойство безопасности: лимит адреса до базы и BCrypt, затем пользователь
    // (неизвестный — сравнение с хэшем-заглушкой), затем пароль. Неизвестный логин и неверный пароль
    // отвечают одинаково; остаточная утечка принята (AGENTS.md §10).
    private User authenticate(String username, String password, String clientIp, Instant now) {
        String cleanEmail = username != null ? username.trim().toLowerCase() : "";
        try (LoginRateLimiter.Attempt ignored = rateLimiter.begin(clientIp, cleanEmail)) {
            log.info("Login attempt for {}", cleanEmail);

            User user = userRepository.findForLoginByUsername(cleanEmail).orElse(null);
            if (user == null) {
                passwordEncoder.matches(password, absentUserPasswordHash);
                recordAddressFailure(clientIp, cleanEmail);
                // В журнал — категория отказа, не пароль; cleanEmail — недоверенный ввод, его обрезает
                // AuditLogService.
                auditLogService.logDenied(AuditEntity.AUTH, cleanEmail, AuditAction.LOGIN, cleanEmail, null,
                        "Login refused: no such account");
                log.warn("Login failed: username {} not found", cleanEmail);
                throw new BusinessException(INVALID_CREDENTIALS);
            }

            boolean lockedOut = user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(now);
            if (!lockedOut && user.getLockoutUntil() != null) {
                // Локаут истёк: попытка ниже считается с нуля.
                log.info("Account lockout expired for username {}. Resetting lockout state.", cleanEmail);
                user.setLockoutUntil(null);
                user.setFailedLoginAttempts(0);
            }

            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                registerFailedAttempt(user, cleanEmail, clientIp, lockedOut, now);
                auditLogService.logDenied(AuditEntity.AUTH, cleanEmail, AuditAction.LOGIN, cleanEmail, user.getCompanyId(),
                        "Login refused: wrong password");
                throw new BusinessException(INVALID_CREDENTIALS);
            }

            // Пароль верен: ответы ниже идут владельцу аккаунта и могут быть точными.
            if (lockedOut) {
                log.warn("Login blocked: account {} is locked until {}", cleanEmail, user.getLockoutUntil());
                auditLogService.logDenied(AuditEntity.AUTH, cleanEmail, AuditAction.LOGIN, cleanEmail, user.getCompanyId(),
                        "Login refused: account locked until " + user.getLockoutUntil());
                throw new BusinessException(ACCOUNT_LOCKED_PREFIX + tryAgainIn(user.getLockoutUntil(), now));
            }
            if (!STATUS_ACTIVE.equals(user.getStatus())) {
                log.warn("Login blocked: account {} is in status {}", cleanEmail, user.getStatus());
                auditLogService.logDenied(AuditEntity.AUTH, cleanEmail, AuditAction.LOGIN, cleanEmail, user.getCompanyId(),
                        "Login refused: account status " + user.getStatus());
                throw new BusinessException(ACCOUNT_NOT_ACTIVE);
            }

            if ((user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0) || user.getLockoutUntil() != null) {
                user.setFailedLoginAttempts(0);
                user.setLockoutUntil(null);
                userRepository.save(user);
            }
            rateLimiter.clearFailuresOf(clientIp, cleanEmail);
            return user;
        }
    }

    private LoginResponse startSession(User user, Instant now) {
        // Вход — активность учётки: отсчёт 90 дней до автоблокировки начинается заново (Р-101).
        user.setLastActivityAt(now);
        userRepository.save(user);
        // Вход начинает новое семейство ротации.
        LoginResponse response = issuePair(user, UUID.randomUUID(), now);
        // Успех пишется наравне с отказами (PCI DSS 10.2). entityId — логин, как у всех AUTH-записей:
        // успех и отказы одного аккаунта находит один фильтр (P3-2).
        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.AUTH, user.getUsername(), AuditAction.LOGIN,
                user.getUsername(), user.getCompanyId(), "Login successful, role " + user.getRole()));
        log.info("Login successful for user ID: {}, role: {}, companyId: {}",
                user.getId(), user.getRole(), user.getCompanyId());
        return response;
    }

    // Неудача считается против адреса всегда, против аккаунта — только вне локаута: иначе любой
    // стучащий держит чужой аккаунт заблокированным, а 30 минут идут не от шестой неудачи (Р-28).
    private void registerFailedAttempt(User user, String cleanEmail, String clientIp, boolean lockedOut, Instant now) {
        recordAddressFailure(clientIp, cleanEmail);

        if (lockedOut) {
            log.warn("Login failed: incorrect password for username {}, already locked until {}",
                    cleanEmail, user.getLockoutUntil());
            return;
        }

        int attempts = (user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0) + 1;
        user.setFailedLoginAttempts(attempts);

        // Шесть неудач, тридцать минут: PCI-DSS 8.3.4 (Р-28). Не подкручивать.
        if (attempts >= 6) {
            user.setLockoutUntil(now.plus(30, ChronoUnit.MINUTES));
            auditLogService.logDenied(AuditEntity.AUTH, cleanEmail, AuditAction.LOCKOUT, cleanEmail, user.getCompanyId(),
                    "Account locked until " + user.getLockoutUntil() + " after " + attempts + " failed attempts");
            log.warn("Account {} locked for 30 minutes due to 6 failed login attempts (PCI-DSS 8.3.4)", cleanEmail);
        } else {
            log.warn("Login failed: incorrect password for username {}. Failed attempts: {}/6", cleanEmail, attempts);
        }

        userRepository.save(user);
    }

    // Журнал — один раз за окно, а не на каждую отбитую попытку: иначе защита стала бы усилителем
    // нагрузки. entityId — логин, адрес уже в client_ip.
    private void recordAddressFailure(String clientIp, String cleanEmail) {
        if (rateLimiter.recordFailure(clientIp, cleanEmail)) {
            auditLogService.logDenied(AuditEntity.AUTH, cleanEmail, AuditAction.RATE_LIMIT, cleanEmail, null,
                    "Address reached the failed-login limit; further attempts refused for the window");
        }
    }

    // Округление вверх: никогда не «попробуйте прямо сейчас».
    private static String tryAgainIn(Instant lockoutUntil, Instant now) {
        long minutes = Math.max(1, (Duration.between(now, lockoutUntil).getSeconds() + 59) / 60);
        return "Please try again in " + minutes + (minutes == 1 ? " minute." : " minutes.");
    }

    // Любой отказ — один 401 с одним текстом. Повтор токена и неактивный пользователь отзывают
    // семейство, и отзыв обязан пережить 401 — отсюда noRollbackFor.
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public LoginResponse refresh(RefreshRequest request) {
        Instant now = Instant.now();

        RefreshToken stored = refreshTokenService.find(request.refreshToken())
                .orElseThrow(() -> {
                    log.warn("Refresh refused: unknown token");
                    return new UnauthorizedException(INVALID_REFRESH_TOKEN);
                });

        if (stored.isExpiredAt(now)) {
            log.warn("Refresh refused: token of user {} expired at {}", stored.getUserId(), stored.getExpiresAt());
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }

        // Отозванный токен = мёртвое семейство. Проверка до грейса: предшественник, предъявленный в
        // грейсе после выхода, не воскрешает сессию. Повтор вне грейса и здесь поднимает тревогу.
        if (stored.isRevoked()) {
            if (stored.isRotated() && isBeyondGrace(stored, now)) {
                log.error("{}: rotated refresh token of user {} reused {} after rotation, family {} already revoked at {}",
                        REFRESH_TOKEN_REUSE_MARKER, stored.getUserId(), Duration.between(stored.getRotatedAt(), now),
                        stored.getFamilyId(), stored.getRevokedAt());
            } else {
                log.warn("Refresh refused: token of user {} belongs to family {} revoked at {}",
                        stored.getUserId(), stored.getFamilyId(), stored.getRevokedAt());
            }
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }

        // Ротированный токен в грейсе — гонка двух вкладок, второй обслуживается как первый; вне
        // грейса — повтор украденного токена, семейство гасится целиком.
        if (stored.isRotated()) {
            Duration sinceRotation = Duration.between(stored.getRotatedAt(), now);
            if (isBeyondGrace(stored, now)) {
                int revoked = refreshTokenService.revokeFamily(stored.getFamilyId(), now);
                // Синхронно: запись о краже не должна зависеть от транзакции, которая кончится 401.
                // Токена в записи нет — только id семейства.
                String login = loginOf(stored.getUserId());
                auditLogService.logDenied(AuditEntity.AUTH, login, AuditAction.TOKEN_REUSE,
                        login, null,
                        "Rotated refresh token reused " + sinceRotation + " after rotation; revoked "
                                + revoked + " token(s) of family " + stored.getFamilyId());
                log.error("{}: rotated refresh token of user {} reused {} after rotation (grace {}); "
                                + "revoked {} token(s) of family {}",
                        REFRESH_TOKEN_REUSE_MARKER, stored.getUserId(), sinceRotation,
                        refreshTokenService.getRotationGrace(), revoked, stored.getFamilyId());
                throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
            }
            log.info("Refresh within grace window ({} after rotation) for user {} — serving as a concurrent-tab race",
                    sinceRotation, stored.getUserId());
        }

        // Подстраховка: блокировка в UserService гасит токены сама, но refresh не-ACTIVE пользователя
        // тоже уносит семейство.
        User user = userRepository.findById(stored.getUserId()).orElse(null);
        if (user == null || !STATUS_ACTIVE.equals(user.getStatus())) {
            refreshTokenService.revokeFamily(stored.getFamilyId(), now);
            log.warn("Refresh refused: user {} is {}; family {} revoked",
                    stored.getUserId(), user == null ? "missing" : user.getStatus(), stored.getFamilyId());
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }
        // Пароль сбросил администратор: сессия со старым не продлевается (Р-100). Подстраховка — сброс
        // гасит токены сам.
        if (user.isPasswordChangeRequired()) {
            refreshTokenService.revokeFamily(stored.getFamilyId(), now);
            log.warn("Refresh refused: user {} must change the password; family {} revoked",
                    stored.getUserId(), stored.getFamilyId());
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }

        // Условный UPDATE, а не save прочитанной сущности: save затёр бы параллельный отзыв и выдал
        // наследника мёртвого семейства. rotated_at — время первой ротации: повтор в грейсе окно не двигает.
        if (!refreshTokenService.markRotated(stored.getId(), now)) {
            log.warn("Refresh refused: token of user {} was revoked while the refresh was in flight (family {})",
                    stored.getUserId(), stored.getFamilyId());
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }
        // Работа без нового входа — тоже активность (Р-101); писать чаще раза в сутки незачем.
        if (user.getLastActivityAt() == null || user.getLastActivityAt().isBefore(now.minus(Duration.ofDays(1)))) {
            user.setLastActivityAt(now);
            userRepository.save(user);
        }
        LoginResponse response = issuePair(user, stored.getFamilyId(), now);
        log.info("Refresh successful for user ID: {}, family {}", user.getId(), stored.getFamilyId());
        return response;
    }

    // Гасит всё семейство. Неизвестный токен отвечает как живой, иначе эндпоинт — оракул
    // существования токенов. Access-токен живёт до срока: чёрного списка нет намеренно (P1-13).
    @Transactional
    public void logout(LogoutRequest request) {
        Instant now = Instant.now();
        refreshTokenService.find(request == null ? null : request.refreshToken()).ifPresentOrElse(
                stored -> {
                    int revoked = refreshTokenService.revokeFamily(stored.getFamilyId(), now);
                    log.info("Logout: user {} family {} — {} token(s) revoked", stored.getUserId(), stored.getFamilyId(), revoked);
                    // Только если вызов закончил сессию: иначе журнал наполнит LOGOUT-мусором любой
                    // прохожий. Событием — запись появится, только если отзыв закоммитился (P3-2).
                    if (revoked > 0) {
                        User user = userRepository.findById(stored.getUserId()).orElse(null);
                        String login = user != null ? user.getUsername() : stored.getUserId().toString();
                        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.AUTH, login,
                                AuditAction.LOGOUT, login, user != null ? user.getCompanyId() : null,
                                "Logout: revoked " + revoked + " token(s) of family " + stored.getFamilyId()));
                    }
                },
                () -> log.info("Logout with unknown or absent refresh token — nothing to revoke"));
    }

    // У AUTH-записей entityId — логин (P3-2).
    private String loginOf(UUID userId) {
        return userRepository.findById(userId).map(User::getUsername).orElse(userId.toString());
    }

    private boolean isBeyondGrace(RefreshToken stored, Instant now) {
        return Duration.between(stored.getRotatedAt(), now).compareTo(refreshTokenService.getRotationGrace()) > 0;
    }

    private LoginResponse issuePair(User user, UUID familyId, Instant now) {
        String accessToken = jwtProvider.generateToken(
                user.getId().toString(),
                user.getUsername(),
                user.getRole(),
                user.getCompanyId(),
                user.isDmsLinksAllowed()
        );
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user.getId(), familyId, now);
        return new LoginResponse(
                accessToken,
                jwtProvider.getExpirationMs() / 1000,
                user.getRole(),
                refresh.token(),
                refreshTokenService.getTtl().toSeconds(),
                false
        );
    }
}
