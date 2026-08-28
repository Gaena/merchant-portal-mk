package az.millikart.common.audit;

import java.util.Set;

// Словарь сущностей журнала, один на три сервиса (P3-2): до него каждый сервис называл сущности
// сам и словарь разъехался. Не enum, а строковые константы: колонка строковая, а enum падал бы на
// чтении значения, которого в нём нет (старая строка после переименования), — журнал, который не
// может показать собственную историю, хуже строки. Значение вне словаря см. в AuditLogService.
public final class AuditEntity {

    // AUTH — всё про аутентификацию: вход, отказ, блокировка, лимит, кража токена, выход. Изменения
    // самой учётной записи — USER: что сделали С аккаунтом и что сделали ИМ — разные вопросы.
    // entityId у AUTH — всегда логин (не UUID и не адрес), у LIST-отказов — "ALL": действие над
    // списком, а не над записью. TERMINAL принадлежит directory, но отказы по нему пишет и pbl.
    public static final String USER = "USER";
    public static final String AUTH = "AUTH";
    public static final String COMPANY = "COMPANY";
    public static final String TERMINAL = "TERMINAL";
    public static final String PAYMENT_LINK = "PAYMENT_LINK";
    public static final String TRANSACTION = "TRANSACTION";
    public static final String AUDIT_LOG = "AUDIT_LOG";

    private static final Set<String> KNOWN =
            Set.of(USER, AUTH, COMPANY, TERMINAL, PAYMENT_LINK, TRANSACTION, AUDIT_LOG);

    private AuditEntity() {
    }

    static boolean isKnown(String value) {
        return value != null && KNOWN.contains(value);
    }
}
