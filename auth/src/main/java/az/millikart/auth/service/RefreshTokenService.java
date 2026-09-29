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

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Хранилище refresh-токенов (P1-12), ротация — в AuthService.refresh. Токен непрозрачный, не JWT
// намеренно: refresh всё равно идёт в базу, а claim'ов, которые можно подсмотреть или подделать, нет.
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    // 256 бит — стойкость HS256-ключа access-токенов.
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final SecureRandom random = new SecureRandom();
    @Getter
    private final Duration ttl;
    @Getter
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

    // Уходит клиенту; сам токен в базе не хранится.
    public record IssuedRefreshToken(String token, Instant expiresAt) {
    }

    // familyId: у входа — новый, у refresh — семейство ротируемого токена, чтобы цепочка отзывалась целиком.
    @Transactional
    public IssuedRefreshToken issue(UUID userId, UUID familyId, Instant now) {
        String token = newToken();
        Instant expiresAt = now.plus(ttl);
        // issued_at здесь, а не @CreationTimestamp: он и expires_at — одно мгновение, а тест старит
        // токен, задавая now.
        repository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash(sha256Hex(token))
                .familyId(familyId)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build());
        return new IssuedRefreshToken(token, expiresAt);
    }

    // Без блокировки: результат — снимок и обратно не сохраняется; строку меняет только markRotated.
    public Optional<RefreshToken> find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(sha256Hex(rawToken));
    }

    // false — токен отозвали после чтения: refresh обязан отказать и не выдавать наследника.
    @Transactional
    public boolean markRotated(UUID tokenId, Instant now) {
        return repository.markRotatedIfLive(tokenId, now) == 1;
    }

    // Блокировка строк семейства — до массового UPDATE: иначе наследник, выпущенный встречным
    // refresh, проскочит живым (P1-12).
    @Transactional
    public int revokeFamily(UUID familyId, Instant now) {
        repository.lockFamily(familyId);
        int revoked = repository.revokeFamily(familyId, now);
        log.info("Revoked {} refresh token(s) of family {}", revoked, familyId);
        return revoked;
    }

    // Блокировка до UPDATE — по той же причине, что в revokeFamily.
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

    // SHA-256, а не BCrypt: у токена 256 бит CSPRNG, подбирать нечего — хэш лишь обесценивает дамп
    // базы. Детерминированный хэш годится ключом поиска (WHERE token_hash = ?), соль BCrypt его исключила бы.
    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", e);
        }
    }
}
