package az.millikart.directory.service;

import az.millikart.directory.domain.AuditLog;
import az.millikart.directory.dto.AuditLogResponse;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.InvalidStateException;

import az.millikart.directory.repository.AuditLogRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millikart.common.security.UserPrincipal;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void logAction(String entityType, String entityId, String action, String performedBy, String companyId, String details) {
        AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .performedBy(performedBy != null ? performedBy : "system")
                .companyId(companyId)
                .details(details)
                .build();
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> listAuditLogs(String entityType, String entityId, UserPrincipal principal) {
        String actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        List<AuditLog> logs;

        if ("SYSTEM_ADMIN".equals(actorRole) || "AUDITOR".equals(actorRole)) {
            if (entityType != null && entityId != null) {
                logs = auditLogRepository.findAllByEntityTypeAndEntityId(entityType, entityId);
            } else {
                logs = auditLogRepository.findAll();
            }
        } else if ("COMPANY_HEAD".equals(actorRole) || "COMPANY_MANAGER".equals(actorRole)) {
            if (actorCompanyId == null) {
                throw new InvalidStateException("Access denied: User not assigned to a company");
            }
            logs = auditLogRepository.findAllByCompanyId(actorCompanyId);
            if (entityType != null && entityId != null) {
                logs = logs.stream()
                        .filter(l -> entityType.equalsIgnoreCase(l.getEntityType()) && entityId.equalsIgnoreCase(l.getEntityId()))
                        .collect(Collectors.toList());
            }
        } else {
            throw new InvalidStateException("Access denied");
        }

        return logs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private AuditLogResponse mapToResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getEntityType(),
                log.getEntityId(),
                log.getAction(),
                log.getPerformedBy(),
                log.getCompanyId(),
                log.getDetails(),
                log.getCreatedAt()
        );
    }
}
