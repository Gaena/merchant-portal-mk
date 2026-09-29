package az.millikart.common.audit;

public enum AuditOutcome {
    SUCCESS,
    DENIED,
    // Исход у эквайера не подтверждён, локальная транзакция откатилась (P3-2): запись разбирают
    // руками. Пишет только AuditLogService.logUnresolved.
    UNRESOLVED
}
