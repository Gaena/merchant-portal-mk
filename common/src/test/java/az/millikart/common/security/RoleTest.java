package az.millikart.common.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

// Role.fromValue стоит между неограниченным входом — claim role в JWT и varchar users.role —
// и каждым решением об авторизации в портале. Два его свойства несущие, и у каждого свой тест
// ниже: он никогда не бросает и сравнивает точно.
class RoleTest {

    @ParameterizedTest
    @EnumSource(Role.class)
    void fromValue_knownRoles_areParsed(Role role) {
        assertEquals(Optional.of(role), Role.fromValue(role.name()));
    }

    @Test
    void fromValue_allFiveRolesExist() {
        assertEquals(5, Role.values().length);
        assertEquals(Optional.of(Role.SYSTEM_ADMIN), Role.fromValue("SYSTEM_ADMIN"));
        assertEquals(Optional.of(Role.COMPANY_HEAD), Role.fromValue("COMPANY_HEAD"));
        assertEquals(Optional.of(Role.COMPANY_MANAGER), Role.fromValue("COMPANY_MANAGER"));
        assertEquals(Optional.of(Role.COMPANY_EMPLOYEE), Role.fromValue("COMPANY_EMPLOYEE"));
        assertEquals(Optional.of(Role.AUDITOR), Role.fromValue("AUDITOR"));
    }

    // MERCHANT_ADMIN и MERCHANT_USER — две роли, которые сторожили
    // GET /payment-links/{id}/transactions, никогда не существуя (P0-4).
    @ParameterizedTest
    @ValueSource(strings = {"MERCHANT_ADMIN", "MERCHANT_USER", "HACKER", "admin", "ROLE_SYSTEM_ADMIN", "SYSTEM"})
    void fromValue_unknownValue_returnsEmpty(String raw) {
        assertTrue(Role.fromValue(raw).isEmpty(), () -> "expected no role for " + raw);
    }

    // Защита от эскалации привилегий: сворачивай разбор регистр, пользователь с сохранённой ролью
    // system_admin молча стал бы администратором. Не ослаблять до equalsIgnoreCase — точное
    // сравнение здесь и есть весь смысл.
    @ParameterizedTest
    @ValueSource(strings = {"system_admin", "System_Admin", "System_admin", "sYSTEM_ADMIN", "auditor", "Auditor"})
    void fromValue_isCaseSensitive(String raw) {
        assertTrue(Role.fromValue(raw).isEmpty(), () -> "case-folded value must not parse: " + raw);
    }

    // Пробелы тоже не обрезаются: значение с padding — другое значение.
    @ParameterizedTest
    @ValueSource(strings = {" SYSTEM_ADMIN", "SYSTEM_ADMIN ", " SYSTEM_ADMIN ", "SYSTEM_ADMIN\n", "\tAUDITOR"})
    void fromValue_doesNotTrim(String raw) {
        assertTrue(Role.fromValue(raw).isEmpty(), () -> "padded value must not parse: " + raw);
    }

    @Test
    void fromValue_nullOrBlank_returnsEmpty() {
        assertTrue(Role.fromValue(null).isEmpty());
        assertTrue(Role.fromValue("").isEmpty());
        assertTrue(Role.fromValue(" ").isEmpty());
        assertTrue(Role.fromValue("   \t\n ").isEmpty());
    }

    // Контракт, благодаря которому мусорный claim — это 403, а не 500: разбор произвольной строки
    // не должен бросать. Role.valueOf бросил бы на каждом из этих входов.
    @Test
    void fromValue_neverThrows() {
        Stream.of(
                null,
                "",
                " ",
                "HACKER",
                "system_admin",
                "SYSTEM_ADMIN; DROP TABLE users",
                "'; --",
                "ROLE_SYSTEM_ADMIN",
                "SYSTEM_ADMIN\0",
                "СИСТЕМНЫЙ_АДМИН",
                "🙂",
                "null",
                "[object Object]",
                "-1",
                "0x00",
                "a".repeat(10_000)
        ).forEach(raw -> assertDoesNotThrow(() -> {
            Role.fromValue(raw);
        }, () -> "threw for input: " + raw));
    }
}
