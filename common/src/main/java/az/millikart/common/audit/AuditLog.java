package az.millikart.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

// Строка журнала, общая для всех сервисов (Р-41). Только дозапись (Р-42): сеттеров нет, у
// AuditLogRepository только save, @Immutable не даёт Hibernate выпустить UPDATE, а билдер не задаёт id —
// иначе save с id существующей записи сделал бы merge и переписал её. Собирать — только через AuditLogService.
@Entity
@Immutable
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    @Column(name = "company_id")
    private String companyId;

    @Column(name = "details", length = 4000)
    private String details;

    // Разрешается ClientIp в ClientIpFilter; вне запроса null.
    @Column(name = "client_ip", length = 45)
    private String clientIp;

    // traceId запроса или прогона планировщика (MDC): по нему запись находит строки логов. Вне того и другого null.
    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16)
    private AuditOutcome outcome = AuditOutcome.SUCCESS;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Builder
    private AuditLog(String entityType, String entityId, String action, String performedBy, String companyId,
                     String details, String clientIp, String traceId, AuditOutcome outcome) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.performedBy = performedBy;
        this.companyId = companyId;
        this.details = details;
        this.clientIp = clientIp;
        this.traceId = traceId;
        this.outcome = outcome != null ? outcome : AuditOutcome.SUCCESS;
    }
}
