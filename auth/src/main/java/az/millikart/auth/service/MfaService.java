package az.millikart.auth.service;

import az.millikart.auth.domain.MfaChallenge;
import az.millikart.auth.domain.User;
import az.millikart.auth.repository.MfaChallengeRepository;
import az.millikart.auth.security.Totp;
import az.millikart.common.security.CredentialCipher;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Второй фактор входа (PCI DSS 8.4.2, Р-141): шаг между паролем и кодом и секрет TOTP. Сессию не выдаёт —
// это AuthService; здесь ни пароля, ни счётчика неудач.
@Service
public class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);

    // 160 бит — длина ключа HMAC-SHA1, которую советует RFC 4226 (§4).
    private static final int SECRET_BYTES = 20;
    private static final int CHALLENGE_TOKEN_BYTES = 32;

    private static final String HOW_TO_FIX = """
            How to fix:
              1. Generate a key:  openssl rand -base64 32
              2. Export it before starting auth:  export MFA_ENCRYPTION_KEY='<generated value>'
              3. Keep it apart from CREDENTIALS_ENCRYPTION_KEY. Changing the key makes the stored second-factor
                 secrets unreadable: every user would have to set the second factor up again.
            The key is read from the MFA_ENCRYPTION_KEY environment variable (property auth.mfa.encryption-key).
            Never commit it.""";

    @Getter
    private final boolean enabled;
    private final Duration challengeTtl;
    private final String issuer;
    private final CredentialCipher cipher;
    private final MfaChallengeRepository challenges;
    private final SecureRandom random = new SecureRandom();

    public MfaService(@Value("${auth.mfa.enabled}") boolean enabled,
                      @Value("${auth.mfa.challenge-ttl}") Duration challengeTtl,
                      @Value("${auth.mfa.issuer}") String issuer,
                      @Value("${auth.mfa.encryption-key}") String encryptionKey,
                      MfaChallengeRepository challenges) {
        if (challengeTtl == null || challengeTtl.isZero() || challengeTtl.isNegative()) {
            throw new IllegalStateException("auth.mfa.challenge-ttl must be a positive duration, got " + challengeTtl);
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("auth.mfa.issuer must not be blank");
        }
        this.enabled = enabled;
        this.challengeTtl = challengeTtl;
        this.issuer = issuer.strip();
        this.cipher = new CredentialCipher(encryptionKey, "Second-factor encryption key (MFA_ENCRYPTION_KEY)", HOW_TO_FIX);
        this.challenges = challenges;
        if (!enabled) {
            log.warn("Second factor at sign-in is DISABLED (auth.mfa.enabled=false) — a password alone opens a session");
        }
    }

    // Секрет и адрес otpauth:// для приложения — один раз, при подключении. Наружу больше не выходит.
    public record Enrollment(String secret, String otpauthUri) {

        @Override
        public String toString() {
            return "Enrollment[secret=***, otpauthUri=***]";
        }
    }

    // Токен уходит клиенту, в базе — только его SHA-256 (как у refresh-токенов).
    @Transactional
    public String issueChallenge(UUID userId, Instant now) {
        byte[] bytes = new byte[CHALLENGE_TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        challenges.save(MfaChallenge.builder()
                .userId(userId)
                .tokenHash(RefreshTokenService.sha256Hex(token))
                .createdAt(now)
                .expiresAt(now.plus(challengeTtl))
                .build());
        return token;
    }

    public Optional<MfaChallenge> findLiveChallenge(String rawToken, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return challenges.findByTokenHash(RefreshTokenService.sha256Hex(rawToken))
                .filter(challenge -> challenge.isLiveAt(now));
    }

    // false — шаг израсходован встречным запросом или истёк: сессию по нему не выдавать.
    @Transactional
    public boolean consumeChallenge(UUID challengeId, Instant now) {
        return challenges.markUsedIfLive(challengeId, now) == 1;
    }

    @Transactional
    public void forgetChallengesOf(UUID userId) {
        challenges.deleteAllByUserId(userId);
    }

    @Transactional
    public int deleteExpiredChallenges(Instant now) {
        int deleted = challenges.deleteAllExpiredBefore(now);
        if (deleted > 0) {
            log.info("Deleted {} expired sign-in challenge(s)", deleted);
        }
        return deleted;
    }

    // Новый секрет, ещё не подтверждённый: подключение завершит первый принятый код. Повторный вызов до него
    // заменяет секрет — пользователь мог закрыть страницу с QR-кодом. Сохраняет вызывающий.
    public Enrollment startEnrollment(User user) {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        String secret = Totp.base32(bytes);
        user.setMfaSecret(cipher.encrypt(secret));
        user.setMfaEnabledAt(null);
        user.setMfaLastStep(null);
        return new Enrollment(secret, otpauthUri(user.getUsername(), secret));
    }

    // Пусто — код не подошёл: неверный, вне окна ±1 шаг или уже принятый.
    public OptionalLong acceptedStep(User user, String code, Instant now) {
        if (user.getMfaSecret() == null) {
            return OptionalLong.empty();
        }
        byte[] secret = Totp.fromBase32(cipher.decrypt(user.getMfaSecret()));
        return Totp.matchingStep(secret, code, Totp.stepAt(now), user.getMfaLastStep());
    }

    // Формат Key Uri приложений-аутентификаторов: метка «издатель:логин», издатель ещё и параметром.
    private String otpauthUri(String account, String secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + Totp.DIGITS + "&period=" + Totp.STEP_SECONDS;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
