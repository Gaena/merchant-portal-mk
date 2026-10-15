package az.millikart.auth.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

// Р-141: коды второго фактора считаем сами, поэтому сверяемся с самим RFC. Ошибка здесь — либо приложение
// пользователя никогда не совпадёт с сервером, либо сервер примет чужой или уже использованный код.
class TotpTest {

    // RFC 6238, приложение B: ключ SHA-1 — ASCII "12345678901234567890".
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    // Таблица RFC даёт 8 цифр; 6-значный код — их последние шесть (остаток от 10^6 делит остаток от 10^8).
    @Test
    void codes_matchTheRfc6238TestVectors() {
        assertEquals("287082", Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(59))));
        assertEquals("081804", Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(1111111109))));
        assertEquals("050471", Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(1111111111))));
        assertEquals("005924", Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(1234567890))));
        assertEquals("279037", Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(2000000000))));
        assertEquals("353130", Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(20000000000L))));
    }

    // Часы телефона расходятся с сервером: соседний шаг принимается, через один — уже нет.
    @Test
    void aCodeIsAcceptedOneStepAroundTheCurrentOne_andNoFurther() {
        long now = 1_000_000;
        assertEquals(OptionalLong.of(now - 1), Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, now - 1), now, null));
        assertEquals(OptionalLong.of(now), Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, now), now, null));
        assertEquals(OptionalLong.of(now + 1), Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, now + 1), now, null));
        assertTrue(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, now - 2), now, null).isEmpty());
        assertTrue(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, now + 2), now, null).isEmpty());
    }

    // PCI DSS 8.5.1: принятый код второй раз не проходит, как и код шага до него.
    @Test
    void aCodeOfAnAcceptedStepOrEarlier_isRefused() {
        long now = 1_000_000;
        assertTrue(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, now), now, now).isEmpty());
        assertTrue(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, now - 1), now, now).isEmpty());
        assertEquals(OptionalLong.of(now + 1), Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, now + 1), now, now));
    }

    @Test
    void anythingButSixDigits_isRefusedWithoutComparing() {
        long now = 1_000_000;
        String code = Totp.code(RFC_SECRET, now);
        assertTrue(Totp.matchingStep(RFC_SECRET, null, now, null).isEmpty());
        assertTrue(Totp.matchingStep(RFC_SECRET, "", now, null).isEmpty());
        assertTrue(Totp.matchingStep(RFC_SECRET, code + "0", now, null).isEmpty());
        assertTrue(Totp.matchingStep(RFC_SECRET, " " + code.substring(1), now, null).isEmpty());
        assertTrue(Totp.matchingStep(RFC_SECRET, "12345a", now, null).isEmpty());
    }

    // RFC 4648, раздел 10, без выравнивания '=': так секрет понимают приложения-аутентификаторы.
    @Test
    void base32_matchesRfc4648_andRoundTrips() {
        assertEquals("", Totp.base32(new byte[0]));
        assertEquals("MY", Totp.base32("f".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("MZXW6YQ", Totp.base32("foob".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("MZXW6YTBOI", Totp.base32("foobar".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Totp.base32(RFC_SECRET));
        assertArrayEquals(RFC_SECRET, Totp.fromBase32("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"));
        assertThrows(IllegalArgumentException.class, () -> Totp.fromBase32("not-base32"));
    }
}
