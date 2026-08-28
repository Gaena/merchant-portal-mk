package az.millikart.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Сторожит стартовые проверки ключа подписи JWT (P0-5). Сервис обязан отказаться работать на
// ключе, который ничего не защищает: слабый или публичный ключ значит, что кто угодно выпустит
// токен с ролью SYSTEM_ADMIN и любым companyId.
class JwtProviderTest {

    private static final String VALID_SECRET = "test-only-jwt-secret-not-used-anywhere-else-0123456789";
    private static final long EXPIRATION_MS = 3_600_000L;

    // Ключ, утёкший в git-историю этого репозитория. Хранится в base64 и раскодируется в рантайме,
    // чтобы сам литерал не вернулся в исходники через тест, который его же и запрещает: страж
    // в JwtProvider сравнивает SHA-256 по той же причине.
    private static String compromisedSecret() {
        return new String(
                Base64.getDecoder().decode(
                        "ZEdocGN5MXBjeTFoTFhObFkzSmxkQzFyWlhrdFptOXlMV3AzZEMxemFXZHVhVzVuTFhCb2IzTndhR0YwWlMxd1ltdz0="),
                StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @DisplayName("blank secret is rejected at construction")
    @ValueSource(strings = {"", "   ", "\t\n"})
    void constructor_blankSecret_throws(String secret) {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtProvider(secret, EXPIRATION_MS));
        assertTrue(ex.getMessage().contains("JWT_SECRET"), "message must name the variable to set: " + ex.getMessage());
    }

    @Test
    @DisplayName("null secret is rejected at construction")
    void constructor_nullSecret_throws() {
        assertThrows(IllegalStateException.class, () -> new JwtProvider(null, EXPIRATION_MS));
    }

    @Test
    @DisplayName("secret shorter than 32 bytes is rejected: HS256 signs with a 256-bit digest")
    void constructor_shortSecret_throws() {
        String thirtyOneBytes = "0123456789012345678901234567890";
        assertEquals(31, thirtyOneBytes.getBytes(StandardCharsets.UTF_8).length);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtProvider(thirtyOneBytes, EXPIRATION_MS));
        assertTrue(ex.getMessage().contains("31"), "message must state the actual length: " + ex.getMessage());
    }

    @Test
    @DisplayName("exactly 32 bytes is accepted: the boundary is inclusive")
    void constructor_secretOfExactlyMinimumLength_isAccepted() {
        String thirtyTwoBytes = "01234567890123456789012345678901";
        new JwtProvider(thirtyTwoBytes, EXPIRATION_MS);
    }

    // Смысл всей задачи: ключ, лежащий в git-истории, публичен и не должен больше ничего
    // подписывать, какой бы длины он ни был. Если этот тест позеленел после "вернул старое
    // значение, чтобы заработало" — сервис снова нараспашку.
    @Test
    @DisplayName("the key leaked into git history is rejected even though it is long enough")
    void constructor_compromisedSecret_throws() {
        String compromised = compromisedSecret();
        assertEquals(68, compromised.getBytes(StandardCharsets.UTF_8).length,
                "sanity check: this is the 68-byte key from the repository history");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtProvider(compromised, EXPIRATION_MS));
        assertTrue(ex.getMessage().contains("git history"), "message must explain why: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("openssl rand"), "message must tell the operator what to do");
    }

    @Test
    @DisplayName("a valid secret produces a working provider: generate and validate agree")
    void generateAndValidate_roundTrip() {
        JwtProvider provider = new JwtProvider(VALID_SECRET, EXPIRATION_MS);

        String token = provider.generateToken("user-1", "head@comp01.com", "COMPANY_HEAD", "comp-01");
        Claims claims = provider.validateAndGetClaims(token);

        assertEquals("head@comp01.com", claims.getSubject());
        assertEquals("user-1", claims.get("userId"));
        assertEquals("COMPANY_HEAD", claims.get("role"));
        assertEquals("comp-01", claims.get("companyId"));
    }

    @Test
    @DisplayName("a token signed with one key does not validate under another")
    void validate_tokenFromDifferentKey_throws() {
        JwtProvider issuer = new JwtProvider(VALID_SECRET, EXPIRATION_MS);
        JwtProvider other = new JwtProvider("another-test-only-secret-0123456789012345", EXPIRATION_MS);

        String token = issuer.generateToken("user-1", "admin@example.com", "SYSTEM_ADMIN", null);

        assertThrows(io.jsonwebtoken.security.SignatureException.class, () -> other.validateAndGetClaims(token));
    }
}
