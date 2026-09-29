package az.millikart.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Пишет успех после коммита (Р-35), в своей транзакции — её открывает recordSuccess.
// fallbackExecution: событие вне транзакции (планировщик, тест) пишется сразу, а не теряется.
@Component
public class AuditLogWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditLogWriter.class);

    private final AuditLogService auditLogService;

    public AuditLogWriter(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    // Исключение из AFTER_COMMIT-слушателя дошло бы до вызывающего и превратило выполненное действие
    // в ошибку. Транзакция — внутри recordSuccess, а не здесь: сбои flush и коммита попадают в catch.
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
