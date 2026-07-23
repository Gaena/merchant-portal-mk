package az.millikart.directory.dto;

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
        Instant createdAt
) {
}
