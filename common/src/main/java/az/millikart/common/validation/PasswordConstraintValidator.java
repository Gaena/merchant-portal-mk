package az.millikart.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class PasswordConstraintValidator implements ConstraintValidator<ValidPassword, String> {

    // Требование PCI-DSS v4.0: минимум 12 символов, заглавная, строчная, цифра и спецсимвол.
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*[0-9].*");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        // Обязательность — дело @NotBlank (создание, смена пароля). В правке пользователя пустое — «не менять»,
        // как считает UserService; иначе пустое поле формы давало 400 политики паролей (USER-EMPTY-PASSWORD).
        if (password == null || password.isBlank()) {
            return true;
        }

        if (password.length() < 12) {
            return false;
        }

        return LOWERCASE_PATTERN.matcher(password).matches()
                && UPPERCASE_PATTERN.matcher(password).matches()
                && DIGIT_PATTERN.matcher(password).matches()
                && SPECIAL_CHAR_PATTERN.matcher(password).matches();
    }
}
