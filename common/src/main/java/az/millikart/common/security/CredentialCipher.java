package az.millikart.common.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// Шифрование пароля компании к провайдеру (Р-93) и секрета второго фактора (Р-141): AES-256-GCM, свой
// случайный IV у каждого значения, в базе — base64(IV ‖ шифротекст с тегом). Не @Component намеренно: экземпляр
// со своим ключом объявляет каждый сервис, которому он нужен, — иначе сервис без ключа не стартовал бы.
public final class CredentialCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static final String HOW_TO_FIX = """
            How to fix:
              1. Generate a key:  openssl rand -base64 32
              2. Export it before starting the service:  export CREDENTIALS_ENCRYPTION_KEY='<generated value>'
              3. Use the SAME value for directory and pbl — directory encrypts company passwords,
                 pbl decrypts them for the acquirer. Changing the key makes stored passwords unreadable.
            The key is read from the CREDENTIALS_ENCRYPTION_KEY environment variable
            (property mp.credentials.encryption-key). Never commit it.""";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CredentialCipher(String base64Key) {
        this(base64Key, "Credentials encryption key", HOW_TO_FIX);
    }

    // Другой ключ со своей подсказкой, как его задать: keyName и howToFix попадают только в отказ на старте.
    public CredentialCipher(String base64Key, String keyName, String howToFix) {
        this.key = new SecretKeySpec(decodeKey(base64Key, keyName, howToFix), "AES");
    }

    // Ключ не логируется и в сообщения не попадает — только его длина.
    private static byte[] decodeKey(String base64Key, String keyName, String howToFix) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    keyName + " is not set. The service cannot start without it.\n" + howToFix);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(keyName + " is not valid base64.\n" + howToFix);
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException(keyName + " is " + bytes.length
                    + " bytes, AES-256 requires exactly " + KEY_BYTES + ".\n" + howToFix);
        }
        return bytes;
    }

    public String encrypt(String plain) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt a credential", e);
        }
    }

    // Чужой ключ и испорченное значение не расшифровываются: GCM проверяет тег, мусор наружу не уходит.
    public String decrypt(String stored) {
        try {
            byte[] bytes = Base64.getDecoder().decode(stored);
            if (bytes.length <= IV_BYTES) {
                throw new IllegalStateException("Stored credential is damaged");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Stored credential cannot be decrypted: the key differs from the one it was encrypted with, "
                            + "or the value is damaged");
        }
    }
}
