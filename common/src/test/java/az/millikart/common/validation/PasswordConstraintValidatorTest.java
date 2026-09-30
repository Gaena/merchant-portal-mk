package az.millikart.common.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Политика паролей (PCI DSS 8.3.6) — каждое правило отдельно. Отказные тесты сервисов берут короткий
// пароль и падают на длине, поэтому снятую проверку заглавной, цифры или спецсимвола не заметил бы никто.
class PasswordConstraintValidatorTest {

    private final PasswordConstraintValidator validator = new PasswordConstraintValidator();

    @Test
    void aPasswordMeetingEveryRule_isAccepted() {
        assertTrue(validator.isValid("Abcdefghij1!", null));
    }

    // 12 — минимум, 11 — уже отказ; остальные правила выполнены в обоих.
    @Test
    void twelveCharactersIsTheMinimum() {
        assertTrue(validator.isValid("Abcdefghi12!", null));
        assertFalse(validator.isValid("Abcdefghi1!", null));
    }

    // Длина выполнена, не хватает ровно одного класса символов.
    @ParameterizedTest
    @ValueSource(strings = {
            "ABCDEFGHIJ1!",   // нет строчной
            "abcdefghij1!",   // нет заглавной
            "Abcdefghijk!",   // нет цифры
            "Abcdefghijk1"    // нет спецсимвола
    })
    void aPasswordMissingOneCharacterClass_isRefused(String password) {
        assertFalse(validator.isValid(password, null), password);
    }

    // Набор общий с фронтендом (utils/password.ts): засчитывается каждый символ из него.
    @ParameterizedTest
    @ValueSource(strings = {"!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "_", "+", "-", "=", "[", "]",
            "{", "}", ";", "'", ":", "\"", "\\", "|", ",", ".", "<", ">", "/", "?"})
    void everyListedSpecialCharacter_counts(String special) {
        assertTrue(validator.isValid("Abcdefghij1" + special, null), special);
    }

    // null пропускается намеренно: обязательность задаёт @NotBlank, а в правке пользователя null — «не менять».
    @Test
    void null_isLeftToTheRequiredCheck() {
        assertTrue(validator.isValid(null, null));
    }
}
