package az.millikart.directory.controller;

import az.millikart.common.security.UserPrincipal;
import az.millikart.directory.dto.AuditLogResponse;
import az.millikart.directory.service.AuditLogService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<AuditLogResponse> list(@RequestParam(value = "entityType", required = false) String entityType,
                                       @RequestParam(value = "entityId", required = false) String entityId,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return auditLogService.listAuditLogs(entityType, entityId, principal);
    }
}
