package az.millikart.common.audit;

import az.millikart.common.web.ClientIpHolder;

// Состоявшееся действие: сервис публикует его внутри своей транзакции, AuditLogWriter пишет после
// её коммита (Р-35). Всё нужное записи собирается здесь, включая адрес клиента, — слушатель уже не
// зависит от того, на каком потоке он работает. Отказы так не ходят: AFTER_COMMIT не срабатывает
// для транзакции, которая откатывается, а это ровно то, что делает отказ (AuditLogService.logDenied).
public record AuditEvent(
        String entityType,
        String entityId,
        String action,
        String performedBy,
        String companyId,
        String details,
        String clientIp
) {

    // Вне запроса держатель пуст и clientIp остаётся null — это штатно, а не ошибка.
    public static AuditEvent of(String entityType, String entityId, String action,
                                String performedBy, String companyId, String details) {
        return new AuditEvent(entityType, entityId, action, performedBy, companyId, details,
                ClientIpHolder.get());
    }
}
