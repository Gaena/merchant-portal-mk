package az.millikart.pbl.service;

import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditEvent;
import az.millikart.common.security.JwtAuthFilter;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.TransactionStatus;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

// Смена статуса платежа или ссылки без отдельного действия — ответ эквайера, истечение срока, исчерпанный лимит
// (журнал аудита, этап 2). Возврат и списание пишут свои записи и сюда не ходят. Исполнитель — вошедший, чей
// запрос запустил опрос («Проверить статус», списание); без входа — system: страница возврата плательщика,
// открытие ссылки, сверка, планировщик. Запись ложится после коммита, как любое событие (Р-35).
@Component
public class StatusChangeAudit {

    private final ApplicationEventPublisher eventPublisher;

    public StatusChangeAudit(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void transaction(UUID transactionId, String companyId, TransactionStatus from, TransactionStatus to,
                            String why) {
        publish(AuditEntity.TRANSACTION, transactionId, companyId, from, to, why);
    }

    public void link(UUID linkId, String companyId, PaymentLinkStatus from, PaymentLinkStatus to, String why) {
        publish(AuditEntity.PAYMENT_LINK, linkId, companyId, from, to, why);
    }

    private void publish(String entityType, UUID entityId, String companyId, Enum<?> from, Enum<?> to, String why) {
        // null исполнителя AuditLogService пишет как system.
        eventPublisher.publishEvent(AuditEvent.of(entityType, String.valueOf(entityId), AuditAction.STATUS_CHANGE,
                MDC.get(JwtAuthFilter.MDC_USER_KEY), companyId, "Status " + from + " -> " + to + ": " + why));
    }
}
