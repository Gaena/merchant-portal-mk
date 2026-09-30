package az.millikart.ecom.controller;

import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.domain.ProviderTerminal;
import az.millikart.ecom.dto.ProviderSyncResponse;
import az.millikart.ecom.dto.ProviderTerminalResponse;
import az.millikart.ecom.repository.ProviderTerminalRepository;
import az.millikart.ecom.service.ProviderLoginSyncService;
import az.millikart.ecom.service.ProviderTerminalSyncService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Слепок из нашей базы, а не живой запрос к шлюзу: экран не должен падать вместе с чужой базой.
// Форма заведения терминала берёт список у directory (Р-96).
@RestController
@RequestMapping("/api/v1/ecom/provider-terminals")
public class ProviderTerminalController {

    private final ProviderTerminalRepository repository;
    private final ProviderTerminalSyncService syncService;
    private final ProviderLoginSyncService loginSyncService;
    private final AuditLogService auditLogService;

    public ProviderTerminalController(ProviderTerminalRepository repository,
                                      ProviderTerminalSyncService syncService,
                                      ProviderLoginSyncService loginSyncService,
                                      AuditLogService auditLogService) {
        this.repository = repository;
        this.syncService = syncService;
        this.loginSyncService = loginSyncService;
        this.auditLogService = auditLogService;
    }

    // includeInactive — для разбора, куда делся знакомый администратору терминал.
    @GetMapping
    public List<ProviderTerminalResponse> list(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireSystemAdmin(principal, AuditAction.LIST, "list provider terminals");
        List<ProviderTerminal> terminals = includeInactive
                ? repository.findAll()
                : repository.findByActiveTrueOrderByTitleAsc();
        return terminals.stream()
                .map(t -> new ProviderTerminalResponse(
                        t.getRid(), t.getTitle(), t.getLogin(), t.getTerminalRid(), t.isActive(), t.getLastSeenAt()))
                .toList();
    }

    // Одна кнопка обновляет оба слепка — терминалов и логинов мультимерчантов (Р-94): её ждут и форма
    // терминала, и форма компании, чей логин только что завели у провайдера.
    @PostMapping("/sync")
    public ProviderSyncResponse sync(@AuthenticationPrincipal UserPrincipal principal) {
        requireSystemAdmin(principal, AuditAction.UPDATE, "sync provider snapshots");
        return ProviderSyncResponse.of(syncService.sync(), loginSyncService.sync());
    }

    // Карта всех мерчантов провайдера, включая чужих, — только SYSTEM_ADMIN.
    private void requireSystemAdmin(UserPrincipal principal, String action, String attempted) {
        if (UserPrincipal.getRole(principal) != Role.SYSTEM_ADMIN) {
            auditLogService.logDenied(AuditEntity.TERMINAL, "ALL", action, UserPrincipal.getUsername(principal),
                    UserPrincipal.getCompanyId(principal),
                    "Denied: role " + UserPrincipal.getRawRole(principal) + " attempted to " + attempted);
            throw new InvalidStateException("Access denied");
        }
    }
}
