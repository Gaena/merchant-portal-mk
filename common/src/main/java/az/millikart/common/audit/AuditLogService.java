package az.millikart.common.audit;

import az.millikart.common.security.TraceIdFilter;
import az.millikart.common.web.ClientIpHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// Пишущая половина журнала, одна на все сервисы (Р-41). Успех пишет AuditLogWriter после коммита;
// отказ и неизвестный исход — здесь, в своей транзакции: их транзакция откатится (Р-35). Внутри
// HTTP-запроса запись ждёт его конца (AuditOutbox, Р-85).
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    // Маркер для мониторинга: каждое появление — потерянная запись журнала.
    public static final String AUDIT_WRITE_FAILED_MARKER = "AUDIT_WRITE_FAILED";

    // Маркер: entityType или action вне словаря AuditEntity/AuditAction (P3-2).
    public static final String AUDIT_OUTSIDE_DICTIONARY_MARKER = "AUDIT_OUTSIDE_DICTIONARY";

    // Ширины колонок из 003-directory-schema.xml / 004-audit-log-ip-and-indexes.xml.
    private static final int ENTITY_TYPE_MAX = 50;
    private static final int ACTION_MAX = 50;
    private static final int ID_MAX = 255;
    private static final int DETAILS_MAX = 4000;

    private final AuditLogRepository auditLogRepository;
    // Звено цепочки — в той же транзакции, что запись (Р-138).
    private final AuditChain chain;

    // Шаблоном, а не @Transactional(REQUIRES_NEW): запись идёт и внутренним вызовом, и отложенно из
    // AuditOutbox — мимо прокси, где аннотация не действует.
    private final TransactionTemplate ownTransaction;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           PlatformTransactionManager transactionManager,
                           AuditChain chain) {
        this.auditLogRepository = auditLogRepository;
        this.chain = chain;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Исключения не ловятся: их репортит AuditLogWriter, операция уже закоммичена. Отложенную запись
    // репортит writeReporting.
    public void recordSuccess(AuditEvent event) {
        warnIfOutsideDictionary(event.entityType(), event.action());
        AuditLog record = AuditLog.builder()
                .entityType(clip(event.entityType(), ENTITY_TYPE_MAX))
                .entityId(clip(event.entityId(), ID_MAX))
                .action(clip(event.action(), ACTION_MAX))
                .performedBy(clip(event.performedBy() != null ? event.performedBy() : "system", ID_MAX))
                .companyId(clip(event.companyId(), ID_MAX))
                .details(clip(event.details(), DETAILS_MAX))
                .clientIp(event.clientIp())
                .traceId(event.traceId())
                .outcome(AuditOutcome.SUCCESS)
                .build();
        if (AuditOutbox.defer(() -> writeReporting(record, "audit record"))) {
            return;
        }
        ownTransaction.executeWithoutResult(status -> chain.seal(auditLogRepository.save(record)));
    }

    // Звать прямо перед throw. companyId — компания актора, не названная в запросе: иначе любой пишет
    // текст в журнал чужой компании. Паролей и токенов в details не класть: журнал читают другие.
    public void logDenied(String entityType, String entityId, String action,
                          String performedBy, String companyId, String details) {
        warnIfOutsideDictionary(entityType, action);
        AuditLog record = AuditLog.builder()
                .entityType(clip(entityType, ENTITY_TYPE_MAX))
                .entityId(clip(entityId, ID_MAX))
                .action(clip(action, ACTION_MAX))
                .performedBy(clip(performedBy != null ? performedBy : "system", ID_MAX))
                .companyId(clip(companyId, ID_MAX))
                .details(clip(details, DETAILS_MAX))
                .clientIp(ClientIpHolder.get())
                .traceId(MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY))
                .outcome(AuditOutcome.DENIED)
                .build();
        Runnable write = () -> writeReporting(record, "denial record");
        if (!AuditOutbox.defer(write)) {
            write.run();
        }
    }

    // Исход не подтвердил эквайер (PaymentOutcomeUnknownException): деньги могли уйти, а транзакция
    // операции откатится, и эта запись — единственный след попытки. UNRESOLVED — разбирают руками.
    public void logUnresolved(String entityType, String entityId, String action,
                              String performedBy, String companyId, String details) {
        warnIfOutsideDictionary(entityType, action);
        AuditLog record = AuditLog.builder()
                .entityType(clip(entityType, ENTITY_TYPE_MAX))
                .entityId(clip(entityId, ID_MAX))
                .action(clip(action, ACTION_MAX))
                .performedBy(clip(performedBy != null ? performedBy : "system", ID_MAX))
                .companyId(clip(companyId, ID_MAX))
                .details(clip(details, DETAILS_MAX))
                .clientIp(ClientIpHolder.get())
                .traceId(MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY))
                .outcome(AuditOutcome.UNRESOLVED)
                .build();
        Runnable write = () -> writeReporting(record, "record");
        if (!AuditOutbox.defer(write)) {
            write.run();
        }
    }

    // Эквайер отклонил возврат или списание (Р-134): иначе попытка не оставляла в журнале следа — успех пишется
    // событием, неизвестный исход — logUnresolved, а отказ шлюза ничем. В details — текст отказа шлюза.
    public void logDeclined(String entityType, String entityId, String action,
                            String performedBy, String companyId, String details) {
        warnIfOutsideDictionary(entityType, action);
        AuditLog record = AuditLog.builder()
                .entityType(clip(entityType, ENTITY_TYPE_MAX))
                .entityId(clip(entityId, ID_MAX))
                .action(clip(action, ACTION_MAX))
                .performedBy(clip(performedBy != null ? performedBy : "system", ID_MAX))
                .companyId(clip(companyId, ID_MAX))
                .details(clip(details, DETAILS_MAX))
                .clientIp(ClientIpHolder.get())
                .traceId(MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY))
                .outcome(AuditOutcome.DECLINED)
                .build();
        Runnable write = () -> writeReporting(record, "decline record");
        if (!AuditOutbox.defer(write)) {
            write.run();
        }
    }

    // Ошибку записи не пробрасывать: журнал не роняет операцию.
    private void writeReporting(AuditLog record, String what) {
        try {
            ownTransaction.executeWithoutResult(status -> chain.seal(auditLogRepository.save(record)));
        } catch (Exception e) {
            log.error("{}: {} lost for {} {} {} by {}: {}",
                    AUDIT_WRITE_FAILED_MARKER, what, record.getAction(), record.getEntityType(),
                    record.getEntityId(), record.getPerformedBy(), e.getMessage(), e);
        }
    }

    // Warn, а не исключение: опечатка в названии события не должна ронять операцию и терять запись.
    private static void warnIfOutsideDictionary(String entityType, String action) {
        if (!AuditEntity.isKnown(entityType)) {
            log.warn("{}: entityType '{}' is not in AuditEntity; the record is written as is",
                    AUDIT_OUTSIDE_DICTIONARY_MARKER, entityType);
        }
        if (!AuditAction.isKnown(action)) {
            log.warn("{}: action '{}' is not in AuditAction; the record is written as is",
                    AUDIT_OUTSIDE_DICTIONARY_MARKER, action);
        }
    }

    // Длину строк из запросов никто не ограничивает: вставка не должна падать и превращать отказ в 500.
    private static String clip(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
}
