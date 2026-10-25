package az.millikart.auth.security;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// TOTP по RFC 6238 — HMAC-SHA1, 6 цифр, шаг 30 с: то, что читают Google Authenticator и совместимые (Р-141).
// Своя реализация, а не библиотека: десятки строк по RFC, проверенные его тестовыми векторами (TotpTest).
public final class Totp {

    public static final int DIGITS = 6;
    public static final long STEP_SECONDS = 30;

    private static final int MODULUS = 1_000_000;
    private static final String ALGORITHM = "HmacSHA1";
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {
    }

    public static long stepAt(Instant time) {
        return Math.floorDiv(time.getEpochSecond(), STEP_SECONDS);
    }

    public static String code(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", binary % MODULUS);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 is not available in this JVM", e);
        }
    }

    // Шаг, которому принадлежит код: текущий ±1 — часы телефона и сервера расходятся. Шаг не позже lastStep не
    // принимается: код дважды не проходит (PCI DSS 8.5.1). Сравнение постоянного времени.
    public static OptionalLong matchingStep(byte[] secret, String code, long currentStep, Long lastStep) {
        if (code == null || code.length() != DIGITS || !code.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return OptionalLong.empty();
        }
        byte[] presented = code.getBytes(StandardCharsets.US_ASCII);
        for (long step = currentStep - 1; step <= currentStep + 1; step++) {
            if (lastStep != null && step <= lastStep) {
                continue;
            }
            if (MessageDigest.isEqual(code(secret, step).getBytes(StandardCharsets.US_ASCII), presented)) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    // RFC 4648 без выравнивания '=': так секрет вводят руками и так его ждут приложения.
    public static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32_ALPHABET.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return out.toString();
    }

    public static byte[] fromBase32(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : text.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Not a base32 character");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
