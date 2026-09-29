package az.millikart.common.security;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Авторизация — только против этих констант: опечатка в строковом литерале невидима компилятору.
// На границе (claim role, users.role) роль — строка, разбор — только fromValue.
public enum Role {
    SYSTEM_ADMIN,
    COMPANY_HEAD,
    COMPANY_MANAGER,
    COMPANY_EMPLOYEE,
    AUDITOR;

    // Сравнение точное, без регистра и trim: "system_admin" в базе не должен стать администратором.
    private static final Map<String, Role> BY_NAME = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

    // Не бросает: нераспознанное — empty, вызывающий обязан отказать, роли по умолчанию нет (P0-4,
    // P2-7). Role.valueOf на внешнем входе не применять: исключение превратит 403 в 500.
    public static Optional<Role> fromValue(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(raw));
    }
}
