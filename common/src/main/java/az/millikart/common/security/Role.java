package az.millikart.common.security;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Пять ролей портала. Решения об авторизации — против этих констант, никогда против строковых
// литералов: опечатка в литерале невидима компилятору и молча даёт или отнимает доступ (так было с
// MERCHANT_ADMIN, которого никогда не существовало). На проводе роль остаётся строкой: claim role в
// JWT и колонка users.role (обычный varchar(50)) ничем не ограничены, разбор идёт через fromValue.
public enum Role {
    SYSTEM_ADMIN,
    COMPANY_HEAD,
    COMPANY_MANAGER,
    COMPANY_EMPLOYEE,
    AUDITOR;

    // Ключи — ровно Enum::name: сравнение точное, без приведения регистра и без trim. Принять
    // "system_admin" как SYSTEM_ADMIN значило бы отдать админские права, а не сделать удобнее.
    private static final Map<String, Role> BY_NAME = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

    // Никогда не бросает: на входе произвольная строка извне, и всё нераспознанное — null, пустое,
    // опечатка, значение от атакующего — даёт empty. Вызывающий обязан отказать; роли по умолчанию
    // нет (P0-4, P2-7), а исключение превратило бы честный 403 в 500. Role.valueOf на таком входе
    // не применять.
    public static Optional<Role> fromValue(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(raw));
    }
}
