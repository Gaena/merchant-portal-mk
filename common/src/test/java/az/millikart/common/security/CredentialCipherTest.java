package az.millikart.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Пароль компании к провайдеру лежит в базе зашифрованным (Р-93). Тест сторожит три вещи: значение
// возвращается как было, в базе нет открытого текста и повторов, а чужой ключ и порча не дают мусора.
class CredentialCipherTest {

    // Тот же ключ, что в тестовых профилях directory и pbl.
    private static final String KEY = "dGVzdC1vbmx5LWNyZWRlbnRpYWxzLWtleS0wMTIzNDU=";
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptedPassword_decryptsBack() {
        CredentialCipher cipher = new CredentialCipher(KEY);

        assertEquals("TerminalSys/Пароль-1234", cipher.decrypt(cipher.encrypt("TerminalSys/Пароль-1234")));
    }

    // Случайный IV: одинаковые пароли двух компаний не видны в базе как одинаковые.
    @Test
    void samePassword_isStoredDifferentlyEachTime_andNeverInTheClear() {
        CredentialCipher cipher = new CredentialCipher(KEY);

        String first = cipher.encrypt("secret-password");
        String second = cipher.encrypt("secret-password");

        assertNotEquals(first, second);
        assertFalse(first.contains("secret-password"));
    }

    @Test
    void anotherKey_cannotDecrypt() {
        String stored = new CredentialCipher(KEY).encrypt("secret-password");

        assertThrows(IllegalStateException.class, () -> new CredentialCipher(OTHER_KEY).decrypt(stored));
    }

    // GCM проверяет тег: изменённый байт — отказ, а не другой пароль.
    @Test
    void damagedValue_cannotBeDecrypted() {
        CredentialCipher cipher = new CredentialCipher(KEY);
        byte[] bytes = Base64.getDecoder().decode(cipher.encrypt("secret-password"));
        bytes[bytes.length - 1] ^= 1;

        assertThrows(IllegalStateException.class,
                () -> cipher.decrypt(Base64.getEncoder().encodeToString(bytes)));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("not base64 at all"));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("c2hvcnQ="));
    }

    // Сервис не стартует без ключа AES-256: пустой, не base64, 16 и 48 байт.
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-base64-!!", "eHh4eHh4eHh4eHh4eHh4eA==",
            "eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5"})
    void unusableKey_refusesToStart(String key) {
        assertThrows(IllegalStateException.class, () -> new CredentialCipher(key));
    }

    @Test
    void missingKey_refusesToStart() {
        assertThrows(IllegalStateException.class, () -> new CredentialCipher(null));
    }
}
