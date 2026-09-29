package az.millikart.common.audit;

import az.millikart.common.web.ClientIpHolder;

// Состоявшееся действие: публикуется в транзакции сервиса, AuditLogWriter пишет после коммита (Р-35).
// Адрес клиента берётся здесь: слушатель от потока не зависит. Отказы так не ходят — AFTER_COMMIT
// на откате не срабатывает (AuditLogService.logDenied).
public record AuditEvent(
        String entityType,
        String entityId,
        String action,
        String performedBy,
        String companyId,
        String details,
        String clientIp
) {

    public static AuditEvent of(String entityType, String entityId, String action,
                                String performedBy, String companyId, String details) {
        return new AuditEvent(entityType, entityId, action, performedBy, companyId, details,
                ClientIpHolder.get());
    }
}
