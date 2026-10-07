package az.millikart.directory.dto;

import az.millikart.common.audit.AuditOutcome;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String entityType,
        String entityId,
        String action,
        String performedBy,
        String companyId,
        String details,
        String clientIp,
        AuditOutcome outcome,
        Instant createdAt,
        // traceId запроса или прогона планировщика: по нему находятся строки логов сервиса; у старых записей null.
        String traceId
) {
}
