package az.millikart.common.audit;

public enum AuditOutcome {
    SUCCESS,
    DENIED,
    // Исход у эквайера не подтверждён, локальная транзакция откатилась (P3-2): запись разбирают
    // руками. Пишет только AuditLogService.logUnresolved.
    UNRESOLVED,
    // Возврат или списание отклонил эквайер (Р-134): деньги не двигались, повтор безопасен. Отдельно от DENIED —
    // тот про отказ портала в доступе. Пишет только AuditLogService.logDeclined.
    DECLINED
}
