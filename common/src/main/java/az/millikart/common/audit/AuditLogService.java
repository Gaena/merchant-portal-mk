package az.millikart.common.audit;

import az.millikart.common.web.ClientIpHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// Пишущая половина журнала, одна на три сервиса (Р-41). Успех: сервис публикует AuditEvent, и
// AuditLogWriter пишет его после коммита — действия, которого не было, в журнале не будет. Отказ и
// операция с неизвестным исходом пишутся синхронно здесь: их транзакция откатится, и AFTER_COMMIT
// уже не сработает (Р-35). Чтобы записать действие, сервису достаточно опубликовать событие.
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

    // Шаблоном, а не @Transactional(REQUIRES_NEW): аннотацию применяет прокси, а в directory вызов
    // идёт из listAuditLogs — метода того же класса, мимо прокси. С аннотацией запись об отказе в
    // чтении журнала молча уходила в readOnly-транзакцию листинга и пропадала с её откатом.
    private final TransactionTemplate ownTransaction;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           PlatformTransactionManager transactionManager) {
        this.auditLogRepository = auditLogRepository;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Исключения намеренно не ловятся: их репортит AuditLogWriter, а операция к этому моменту
    // уже закоммичена, и ломаться нечему.
    public void recordSuccess(AuditEvent event) {
        warnIfOutsideDictionary(event.entityType(), event.action());
        ownTransaction.executeWithoutResult(status -> auditLogRepository.save(AuditLog.builder()
                .entityType(clip(event.entityType(), ENTITY_TYPE_MAX))
                .entityId(clip(event.entityId(), ID_MAX))
                .action(clip(event.action(), ACTION_MAX))
                .performedBy(clip(event.performedBy() != null ? event.performedBy() : "system", ID_MAX))
                .companyId(clip(event.companyId(), ID_MAX))
                .details(clip(event.details(), DETAILS_MAX))
                .clientIp(event.clientIp())
                .outcome(AuditOutcome.SUCCESS)
                .build()));
    }

    // Вызывать прямо перед throw. companyId — компания АКТОРА, никогда не названная в запросе:
    // журнал режется по этой колонке, иначе любой аутентифицированный пишет произвольный текст в
    // обзор аудита чужой компании (что пытались сделать — в entityId и details). Секрета в details
    // быть не может: пишут места, где пароль или токен в области видимости, а журнал читают чужие.
    public void logDenied(String entityType, String entityId, String action,
                          String performedBy, String companyId, String details) {
        warnIfOutsideDictionary(entityType, action);
        try {
            ownTransaction.executeWithoutResult(status -> auditLogRepository.save(AuditLog.builder()
                    .entityType(clip(entityType, ENTITY_TYPE_MAX))
                    .entityId(clip(entityId, ID_MAX))
                    .action(clip(action, ACTION_MAX))
                    .performedBy(clip(performedBy != null ? performedBy : "system", ID_MAX))
                    .companyId(clip(companyId, ID_MAX))
                    .details(clip(details, DETAILS_MAX))
                    .clientIp(ClientIpHolder.get())
                    .outcome(AuditOutcome.DENIED)
                    .build()));
        } catch (Exception e) {
            log.error("{}: denial record lost for {} {} {} by {}: {}",
                    AUDIT_WRITE_FAILED_MARKER, action, entityType, entityId, performedBy,
                    e.getMessage(), e);
        }
    }

    // Операция, исход которой не подтвердил эквайер (PaymentOutcomeUnknownException в pbl): деньги
    // могли уйти, локального следа не осталось. Пишется синхронно — транзакция сейчас откатится, и
    // это единственное свидетельство, что операцию вообще пытались провести. outcome = UNRESOLVED,
    // а не SUCCESS: эту запись разбирают руками.
    public void logUnresolved(String entityType, String entityId, String action,
                              String performedBy, String companyId, String details) {
        warnIfOutsideDictionary(entityType, action);
        try {
            ownTransaction.executeWithoutResult(status -> auditLogRepository.save(AuditLog.builder()
                    .entityType(clip(entityType, ENTITY_TYPE_MAX))
                    .entityId(clip(entityId, ID_MAX))
                    .action(clip(action, ACTION_MAX))
                    .performedBy(clip(performedBy != null ? performedBy : "system", ID_MAX))
                    .companyId(clip(companyId, ID_MAX))
                    .details(clip(details, DETAILS_MAX))
                    .clientIp(ClientIpHolder.get())
                    .outcome(AuditOutcome.UNRESOLVED)
                    .build()));
        } catch (Exception e) {
            log.error("{}: record lost for {} {} {} by {}: {}",
                    AUDIT_WRITE_FAILED_MARKER, action, entityType, entityId, performedBy,
                    e.getMessage(), e);
        }
    }

    // Warn, а не исключение: аудит не имеет права уронить бизнес-операцию из-за опечатки в названии
    // события, а отказ записать потерял бы её целиком. Предупреждение — всё принуждение словаря.
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

    // Строки приходят из тел запросов и форм входа, длину им никто не ограничивает: вставка не
    // должна падать на ней и превращать отказ в 500.
    private static String clip(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
}
