package az.millikart.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Пишет запись об успешном действии после коммита его транзакции (Р-35), в своей транзакции —
// её открывает recordSuccess. fallbackExecution: событие, опубликованное вне транзакции (прямой
// вызов сервиса из планировщика или теста), пишется сразу, а не теряется молча.
@Component
public class AuditLogWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditLogWriter.class);

    private final AuditLogService auditLogService;

    public AuditLogWriter(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    // Ошибка записи не должна ломать бизнес-операцию: она уже закоммичена, а исключение из
    // AFTER_COMMIT-слушателя дойдёт до вызывающего и превратит выполненное действие в ошибку.
    // Потому и транзакция внутри recordSuccess, а не на этом методе, — чтобы сбои на flush и
    // коммите попадали внутрь этого catch, а не после него.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AuditEvent event) {
        try {
            auditLogService.recordSuccess(event);
        } catch (Exception e) {
            log.error("{}: audit record lost for {} {} {} by {}: {}",
                    AuditLogService.AUDIT_WRITE_FAILED_MARKER, event.action(), event.entityType(),
                    event.entityId(), event.performedBy(), e.getMessage(), e);
        }
    }
}
