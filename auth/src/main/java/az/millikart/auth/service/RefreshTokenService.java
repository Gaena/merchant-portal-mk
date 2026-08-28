package az.millikart.auth.service;

import az.millikart.auth.domain.RefreshToken;
import az.millikart.auth.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Хранилище refresh-токенов (P1-12): выпуск, поиск по значению, отзыв семейством или пользователем,
// удаление просроченных; сам алгоритм ротации — в AuthService.refresh. Токен непрозрачный (32 байта
// SecureRandom, base64url без padding, 43 символа), не JWT намеренно: каждый refresh всё равно идёт
// в базу, а непрозрачный токен не несёт claim'ов, которые можно подсмотреть или подделать.
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    // 256 бит случайности — тот же класс стойкости, что у HS256-ключа для access-токенов.
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl;
    private final Duration rotationGrace;

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${auth.refresh.ttl}") Duration ttl,
                               @Value("${auth.refresh.rotation-grace}") Duration rotationGrace) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException("auth.refresh.ttl must be a positive duration, got " + ttl);
        }
        if (rotationGrace == null || rotationGrace.isNegative()) {
            throw new IllegalStateException("auth.refresh.rotation-grace must not be negative, got " + rotationGrace);
        }
        this.repository = repository;
        this.ttl = ttl;
        this.rotationGrace = rotationGrace;
    }

    // То, что уходит клиенту: сам токен (в базе его нет никогда) и момент, когда он перестанет
    // работать.
    public record IssuedRefreshToken(String token, Instant expiresAt) {
    }

    public Duration getTtl() {
        return ttl;
    }

    public Duration getRotationGrace() {
        return rotationGrace;
    }

    // Вход передаёт свежий UUID и этим начинает семейство; refresh передаёт семейство ротируемого
    // токена, чтобы цепочка прослеживалась и отзывалась целиком.
    @Transactional
    public IssuedRefreshToken issue(UUID userId, UUID familyId, Instant now) {
        String token = newToken();
        Instant expiresAt = now.plus(ttl);
        // issued_at ставится здесь, а не @CreationTimestamp (как принято в остальном коде): он и
        // expires_at обязаны быть одного мгновения, а тест — уметь состарить токен, задав now.
        repository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash(sha256Hex(token))
                .familyId(familyId)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build());
        return new IssuedRefreshToken(token, expiresAt);
    }

    // Чтение без блокировки: AuthService.refresh обращается с результатом как со снимком и никогда
    // не сохраняет его обратно — единственная запись в строку идёт условным UPDATE (markRotated).
    public Optional<RefreshToken> find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(sha256Hex(rawToken));
    }

    // false, если токен успели отозвать между чтением в AuthService.refresh и этим вызовом (logout,
    // блокировка, обнаружение повтора): тогда refresh обязан отказать и не выдавать наследника.
    // Условный UPDATE, а не save прочитанной сущности — см. markRotatedIfLive.
    @Transactional
    public boolean markRotated(UUID tokenId, Instant now) {
        return repository.markRotatedIfLive(tokenId, now) == 1;
    }

    // Гасит всё живое семейство: logout (сессия кончается целиком, а не шагом ротации), повтор
    // ротированного токена (цепочка считается краденой), refresh от не-ACTIVE пользователя.
    // Блокировка строк семейства идёт ДО массового UPDATE — против гонки с встречным refresh
    // (P1-12): без неё отзыв отчитается «семейство погашено», а свежий наследник проскочит живым.
    @Transactional
    public int revokeFamily(UUID familyId, Instant now) {
        repository.lockFamily(familyId);
        int revoked = repository.revokeFamily(familyId, now);
        log.info("Revoked {} refresh token(s) of family {}", revoked, familyId);
        return revoked;
    }

    // Гасит все сессии пользователя. Зовётся из UserService при уходе из ACTIVE (блокировка,
    // удаление): без этого заблокированный просто продолжал бы обновляться. Блокировка до UPDATE —
    // по той же причине, что в revokeFamily.
    @Transactional
    public int revokeAllForUser(UUID userId, Instant now) {
        repository.lockAllForUser(userId);
        int revoked = repository.revokeAllForUser(userId, now);
        if (revoked > 0) {
            log.info("Revoked {} refresh token(s) of user {}", revoked, userId);
        }
        return revoked;
    }

    @Transactional
    public int deleteExpired(Instant now) {
        int deleted = repository.deleteAllExpiredBefore(now);
        if (deleted > 0) {
            log.info("Deleted {} expired refresh token(s)", deleted);
        }
        return deleted;
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256, а не BCrypt: у токена 256 бит CSPRNG — брутфорсить и подбирать по словарю нечего, от
    // хэша нужно лишь обесценить дамп базы. Он детерминирован и потому годится уникальным индексным
    // ключом поиска (WHERE token_hash = ?); соль BCrypt такой поиск исключила бы, а его cost-фактор
    // добавил бы 100 мс на каждый refresh задаром.
    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 обязателен по спецификации JDK; если его нет — сломана платформа.
            throw new IllegalStateException("SHA-256 is not available in this JVM", e);
        }
    }
}
