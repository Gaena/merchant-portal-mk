package az.millikart.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    // HS256 подписывает 256-битным дайджестом: ключ короче не добавляет стойкости.
    private static final int MIN_SECRET_BYTES = 32;

    // SHA-256 ключа подписи, утёкшего в git-историю этого репозитория (есть с коммита f6a7a7d,
    // коммит «Secret removed» его не вычистил). Хранится дайджестом, а не литералом, чтобы
    // удаление ключа из исходников не положило его обратно.
    private static final String COMPROMISED_SECRET_SHA256 =
            "d29195138e093fd14e29672b4e8dce022359291148322fa3aa93c48a08ed5409";

    private static final String HOW_TO_FIX = """
            How to fix:
              1. Generate a key:  openssl rand -base64 48
              2. Export it before starting the service:  export JWT_SECRET='<generated value>'
              3. Use the SAME value for all three services (auth, directory, pbl) — tokens issued by
                 auth are verified by directory and pbl, and a mismatch rejects every request as 401.
            The key is read from the JWT_SECRET environment variable (property pbl.security.jwt.secret).
            Never commit it: put it in the environment only, see .env.example.""";

    private final Key signingKey;
    // Выставлено наружу, чтобы expiresIn в ответе логина выводился из того же значения, которым
    // подписан токен, а не из константы, которая разъедется с конфигурацией.
    @Getter
    private final long expirationMs;

    public JwtProvider(
            @Value("${pbl.security.jwt.secret}") String secret,
            @Value("${pbl.security.jwt.expiration-ms:86400000}") long expirationMs) {
        validateSecret(secret);
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // Сервис намеренно не стартует без JWT_SECRET и значения по умолчанию не имеет (P0-5): дефолт
    // означал бы, что подпись подделает любой, у кого есть исходники. Слабый или публичный ключ —
    // это молча принимаемые поддельные токены с ролью SYSTEM_ADMIN, поэтому единственный
    // безопасный исход — падение старта с инструкцией оператору. Сам секрет никуда не логируется.
    private static void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT signing secret is not set. The service cannot start without it.\n" + HOW_TO_FIX);
        }
        int length = secret.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT signing secret is too short: " + length + " bytes, HS256 requires at least "
                            + MIN_SECRET_BYTES + ".\n" + HOW_TO_FIX);
        }
        if (COMPROMISED_SECRET_SHA256.equals(sha256Hex(secret))) {
            throw new IllegalStateException(
                    "JWT signing secret is the key that leaked into this repository's git history. "
                            + "It is public: anyone with the repository can sign a token with role SYSTEM_ADMIN "
                            + "and any companyId. Rotate it — this value must never be used again.\n" + HOW_TO_FIX);
        }
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 обязателен по спецификации JDK; если его нет — сломана сама платформа.
            throw new IllegalStateException("SHA-256 is not available in this JVM", e);
        }
    }

    public String generateToken(String userId, String username, String role, String companyId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        if (companyId != null) {
            claims.put("companyId", companyId);
        }

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims validateAndGetClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
