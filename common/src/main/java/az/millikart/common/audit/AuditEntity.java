package az.millikart.common.audit;

import java.util.Set;

// Словарь сущностей журнала, один на все сервисы (P3-2). Не enum: колонка строковая, и enum падал бы
// на чтении значения вне словаря (старая строка после переименования).
public final class AuditEntity {

    // AUTH — аутентификация (вход, локаут, лимит, кража токена, выход), изменения учётки — USER.
    // entityId у AUTH — всегда логин, у LIST-отказов — "ALL". Отказы по TERMINAL пишет и pbl.
    public static final String USER = "USER";
    public static final String AUTH = "AUTH";
    public static final String COMPANY = "COMPANY";
    public static final String TERMINAL = "TERMINAL";
    public static final String PAYMENT_LINK = "PAYMENT_LINK";
    public static final String TRANSACTION = "TRANSACTION";
    public static final String AUDIT_LOG = "AUDIT_LOG";
    // Заказ выписки провайдера, которого нет среди операций портала (Р-125): entityId — номер заказа.
    public static final String PROVIDER_ORDER = "PROVIDER_ORDER";
    // Запуск и остановка сервиса (Р-136): entityId — экземпляр «сервис@хост».
    public static final String SERVICE = "SERVICE";

    private static final Set<String> KNOWN =
            Set.of(USER, AUTH, COMPANY, TERMINAL, PAYMENT_LINK, TRANSACTION, AUDIT_LOG, PROVIDER_ORDER, SERVICE);

    private AuditEntity() {
    }

    static boolean isKnown(String value) {
        return value != null && KNOWN.contains(value);
    }
}
