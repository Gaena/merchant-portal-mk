package az.millikart.directory.dto;

import az.millikart.common.audit.AuditOutcome;
import java.time.Instant;

// Фильтры журнала, уже разобранные контроллером: null — фильтра нет. search и performedBy — после
// SearchTerms.normalize; companyId сервис применяет только у SYSTEM_ADMIN и AUDITOR.
public record AuditLogFilter(
        String entityType,
        String entityId,
        String search,
        AuditOutcome outcome,
        Instant from,
        Instant to,
        String action,
        String performedBy,
        String companyId,
        // «Требует внимания» (Р-137): неподтверждённое и признаки атаки на вход — AuditLogQueryService.
        boolean attention
) {
}
