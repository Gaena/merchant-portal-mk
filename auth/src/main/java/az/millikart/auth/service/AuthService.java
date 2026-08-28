package az.millikart.auth.service;

import az.millikart.auth.domain.RefreshToken;
import az.millikart.auth.domain.User;
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

    // Маркер мониторинга: ротированный refresh-токен предъявлен снова вне грейса. Это не ошибка
    // клиента — прежний держатель уже сходил по нему дальше, значит копия есть у кого-то ещё.
    static final String REFRESH_TOKEN_REUSE_MARKER = "REFRESH_TOKEN_REUSE";

    // Один текст на любой отказ: клиент не должен узнать, ПОЧЕМУ токен отвергнут.
    private static final String INVALID_REFRESH_TOKEN = "Invalid refresh token";

    private static final String STATUS_ACTIVE = "ACTIVE";

    // Один ответ и на «нет такого пользователя», и на «неверный пароль». Разные тексты или разные
    // коды — оракул перечисления аккаунтов: почта мерчанта не секрет, а вот у кого здесь есть
    // учётная запись — секрет.
    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    // Говорится только тому, кто уже доказал пароль, — потому можно конкретно.
    private static final String ACCOUNT_LOCKED_PREFIX = "Account is locked due to multiple failed login attempts. ";

    // Намеренно не называет статус: «заблокирован» и «удалён» — разные сведения о человеке, и нужны
    // они администратору, а не вызывающему.
    private static final String ACCOUNT_NOT_ACTIVE = "Account is not active. Please contact your administrator.";

    // Настоящий BCrypt-хэш случайной строки, которой никто не знает, — на случай несуществующего
    // логина. Его работа — сжечь те же ~80 мс, что matches тратит на настоящем аккаунте: без него
    // неизвестный логин отвечает на порядок быстрее известного, и одинаковый текст ошибки не значит
    // ничего — всё расскажут часы. Ничему не соответствует; не «чинить», сделав выводимым.
    private static final String ABSENT_USER_PASSWORD_HASH =
            "$2a$10$RvlUdzsjEzQg7hkn6vKLe.CjBwtkZ2GCIsbWtBM96m2q/jjEDRjlG";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiter rateLimiter;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       RefreshTokenService refreshTokenService,
                       LoginRateLimiter rateLimiter,
                       AuditLogService auditLogService,
                       ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiter = rateLimiter;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }

    // Порядок проверок и есть свойство безопасности: лимит по адресу до базы и до BCrypt, затем
    // поиск пользователя (неизвестный — сравнение с хэшем-заглушкой), затем пароль. Неизвестный
    // логин и неверный пароль отвечают ОДИНАКОВО — это и мешает эндпоинту перечислить мерчантов.
    // Остаточная утечка (верный пароль к заблокированному аккаунту отличим) принята — problems.md §7.
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request, String clientIp) {
        // Ничего из этого не трогает базу и хэшер — в том и смысл.
        rateLimiter.checkAllowed(clientIp);

        String cleanEmail = request.username() != null ? request.username().trim().toLowerCase() : "";
        Instant now = Instant.now();
        log.info("Login attempt from {} for email: {}", clientIp, cleanEmail);

        User user = userRepository.findByUsername(cleanEmail).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), ABSENT_USER_PASSWORD_HASH);
            recordAddressFailure(clientIp, cleanEmail);
            // Категория отказа, но никогда не введённый пароль. cleanEmail — недоверенный ввод: его
            // режет по ширине колонки AuditLogService, и он нигде не интерпретируется.
            auditLogService.logDenied(AuditEntity.AUTH, cleanEmail, AuditAction.LOGIN, cleanEmail, null,
                    "Login refused: no such account");
            log.warn("Login failed from {}: username {} not found", clientIp, cleanEmail);
            throw new BusinessException(INVALID_CREDENTIALS);
        }

        boolean lockedOut = user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(now);
        if (!lockedOut && user.getLockoutUntil() != null) {
            // Локаут истёк: забываем его сейчас, чтобы попытка ниже считалась с нуля.
            log.info("Account lockout expired for username {}. Resetting lockout state.", cleanEmail);
            user.setLockoutUntil(null);
            user.setFailedLoginAttempts(0);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user, cleanEmail, clientIp, lockedOut, now);
            auditLogService.logDenied(AuditEntity.AUTH, cleanEmail, AuditAction.LOGIN, cleanEmail, user.getCompanyId(),
                    "Login refused: wrong password");
            throw new BusinessException(INVALID_CREDENTIALS);
        }

        // Пароль верен, поэтому следующие два ответа идут владельцу аккаунта и могут быть точными.
        if (lockedOut) {
            log.warn("Login blocked from {}: account {} is locked until {}", clientIp, cleanEmail, user.getLockoutUntil());
            auditLogService.logDenied(AuditEntity.AUTH, cleanEmail, AuditAction.LOGIN, cleanEmail, user.getCompanyId(),
                    "Login refused: account locked until " + user.getLockoutUntil());
            throw new BusinessException(ACCOUNT_LOCKED_PREFIX + tryAgainIn(user.getLockoutUntil(), now));
        }
        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            log.warn("Login blocked from {}: account {} is in status {}", clientIp, cleanEmail, user.getStatus());
            auditLogService.logDenied(AuditEntity.AUTH, cleanEmail, AuditAction.LOGIN, cleanEmail, user.getCompanyId(),
                    "Login refused: account status " + user.getStatus());
            throw new BusinessException(ACCOUNT_NOT_ACTIVE);
        }

        // Успех обнуляет счётчик аккаунта и счётчик адреса.
        if ((user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0) || user.getLockoutUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockoutUntil(null);
            userRepository.save(user);
        }
        rateLimiter.reset(clientIp);

        // Вход начинает новое семейство ротации; все последующие refresh остаются внутри него.
        LoginResponse response = issuePair(user, UUID.randomUUID(), now);
        // PCI-DSS 10.2 требует успехи не меньше отказов — вторжение выглядит как успешный вход не
        // оттуда. entityId — логин, как у всех AUTH-записей (P3-2): успех и отказы одного аккаунта
        // обязаны отвечать одному фильтру, а отказ UUID не знает.
        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.AUTH, user.getUsername(), AuditAction.LOGIN,
                user.getUsername(), user.getCompanyId(), "Login successful, role " + user.getRole()));
        log.info("Login successful from {} for user ID: {}, role: {}, companyId: {}",
                clientIp, user.getId(), user.getRole(), user.getCompanyId());
        return response;
    }

    // Неверный пароль считается против адреса всегда, против аккаунта — только пока тот не в
    // локауте: иначе Р-28 перестанет быть верным буквально (тридцать минут от шестой неудачи, а не
    // от последней попытки кого угодно), и чужой аккаунт держит заблокированным любой стучащий.
    private void registerFailedAttempt(User user, String cleanEmail, String clientIp, boolean lockedOut, Instant now) {
        recordAddressFailure(clientIp, cleanEmail);

        if (lockedOut) {
            log.warn("Login failed from {}: incorrect password for username {}, already locked until {}",
                    clientIp, cleanEmail, user.getLockoutUntil());
            return;
        }

        int attempts = (user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0) + 1;
        user.setFailedLoginAttempts(attempts);

        // Шесть неудач, тридцать минут: PCI-DSS 8.3.4 (Р-28). Не подкручивать.
        if (attempts >= 6) {
            user.setLockoutUntil(now.plus(30, ChronoUnit.MINUTES));
            auditLogService.logDenied(AuditEntity.AUTH, cleanEmail, AuditAction.LOCKOUT, cleanEmail, user.getCompanyId(),
                    "Account locked until " + user.getLockoutUntil() + " after " + attempts + " failed attempts");
            log.warn("Account {} locked for 30 minutes due to 6 failed login attempts (PCI-DSS 8.3.4), last from {}",
                    cleanEmail, clientIp);
        } else {
            log.warn("Login failed from {}: incorrect password for username {}. Failed attempts: {}/6",
                    clientIp, cleanEmail, attempts);
        }

        userRepository.save(user);
    }

    // Запись в журнал — один раз за окно, а не на каждую отбитую попытку: остальные отбивает
    // checkAllowed до всего этого, и строка на каждую сделала бы журнал тем самым усилителем,
    // ради предотвращения которого лимитер и стоит. entityId — логин, адрес уже в client_ip.
    private void recordAddressFailure(String clientIp, String cleanEmail) {
        if (rateLimiter.recordFailure(clientIp)) {
            auditLogService.logDenied(AuditEntity.AUTH, cleanEmail, AuditAction.RATE_LIMIT, cleanEmail, null,
                    "Address reached the failed-login limit; further attempts refused for the window");
        }
    }

    // Округление вверх, чтобы никогда не читалось как «попробуйте прямо сейчас».
    private static String tryAgainIn(Instant lockoutUntil, Instant now) {
        long minutes = Math.max(1, (Duration.between(now, lockoutUntil).getSeconds() + 59) / 60);
        return "Please try again in " + minutes + (minutes == 1 ? " minute." : " minutes.");
    }

    // Ротация refresh-токена. Любой отказ — один и тот же 401 с одним текстом. Порядок проверок
    // важен: обнаружение повтора и проверка статуса пользователя отзывают семейство побочным
    // эффектом, и этот отзыв обязан пережить следующий за ним 401 — отсюда noRollbackFor.
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

        // Отзыв ставится только на всё семейство, поэтому отозванный токен = мёртвое семейство.
        // Проверка стоит ДО грейса намеренно: ротированный предшественник, воспроизведённый внутри
        // грейса после выхода, не должен воскрешать сессию. Тревогу мёртвое семейство всё ещё
        // должно поднять — иначе мониторинг ослепнет оттого, что пользователь успел выйти.
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

        // Уже ротирован: внутри грейса это гонка двух вкладок с одним токеном, и второй обслуживают
        // как первый. Вне грейса — повторное использование отставленного токена: его держатель
        // получил токен не от нас в этой сессии, и семейство гасится целиком.
        if (stored.isRotated()) {
            Duration sinceRotation = Duration.between(stored.getRotatedAt(), now);
            if (isBeyondGrace(stored, now)) {
                int revoked = refreshTokenService.revokeFamily(stored.getFamilyId(), now);
                // Пишется синхронно: это единственное событие аутентификации со смыслом «кража», и
                // оно не должно зависеть от транзакции, которая кончается 401. Ни части токена в
                // записи нет — id семейства наш, токен не наш. Под логином, как все AUTH-записи.
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

        // Закрывает дыру «заблокированный работает ещё сутки»: refresh не-ACTIVE пользователя
        // отказывается и уносит семейство. UserService гасит токены и сам при блокировке — это
        // подстраховка на случай, если тот путь обойдут.
        User user = userRepository.findById(stored.getUserId()).orElse(null);
        if (user == null || !STATUS_ACTIVE.equals(user.getStatus())) {
            refreshTokenService.revokeFamily(stored.getFamilyId(), now);
            log.warn("Refresh refused: user {} is {}; family {} revoked",
                    stored.getUserId(), user == null ? "missing" : user.getStatus(), stored.getFamilyId());
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }

        // Отставка — условный UPDATE (markRotatedIfLive), а не save прочитанной сущности: он берёт
        // блокировку строки и не меняет ничего, если семейство успели отозвать, — иначе мы записали
        // бы поверх свой устаревший revoked_at = NULL и выдали наследника мёртвого семейства.
        // rotated_at хранит время ПЕРВОЙ ротации: повтор внутри грейса не двигает окно вперёд.
        if (!refreshTokenService.markRotated(stored.getId(), now)) {
            log.warn("Refresh refused: token of user {} was revoked while the refresh was in flight (family {})",
                    stored.getUserId(), stored.getFamilyId());
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }
        LoginResponse response = issuePair(user, stored.getFamilyId(), now);
        log.info("Refresh successful for user ID: {}, family {}", user.getId(), stored.getFamilyId());
        return response;
    }

    // Гасит всё семейство, а не предъявленный токен. Всегда 204 — неизвестный токен отвечает как
    // живой, иначе эндпоинт станет оракулом существования токенов. Access-токен живёт до своего
    // срока: его проверяют по подписи, чёрного списка нет намеренно (P1-13).
    @Transactional
    public void logout(LogoutRequest request) {
        Instant now = Instant.now();
        refreshTokenService.find(request == null ? null : request.refreshToken()).ifPresentOrElse(
                stored -> {
                    int revoked = refreshTokenService.revokeFamily(stored.getFamilyId(), now);
                    log.info("Logout: user {} family {} — {} token(s) revoked", stored.getUserId(), stored.getFamilyId(), revoked);
                    // Пишем, только если этот вызов действительно закончил сессию (revoked > 0):
                    // 204 на что угодно — маскировка, а маскировка не имеет права оставлять следы,
                    // иначе журнал наполнит LOGOUT-мусором любой прохожий. Событием, а не
                    // синхронно: запись должна появиться, только если отзыв закоммитился (P3-2).
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

    // Логин по id пользователя: у AUTH-записей в entityId всегда логин (P3-2).
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
                user.getCompanyId()
        );
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user.getId(), familyId, now);
        return new LoginResponse(
                accessToken,
                jwtProvider.getExpirationMs() / 1000,
                user.getRole(),
                refresh.token(),
                refreshTokenService.getTtl().toSeconds()
        );
    }
}
