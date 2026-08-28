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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

// Строка журнала аудита, общая для трёх сервисов (Р-41). Таблица только на дозапись (Р-42): потому
// здесь нет сеттеров — прочитанную запись нельзя изменить и сохранить поверх, — а у
// AuditLogRepository нет ничего, кроме save. Собирать записи только через AuditLogService.
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16)
    @Builder.Default
    private AuditOutcome outcome = AuditOutcome.SUCCESS;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
