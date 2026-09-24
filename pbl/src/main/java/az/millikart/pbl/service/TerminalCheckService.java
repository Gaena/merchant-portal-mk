package az.millikart.pbl.service;

import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditEvent;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.exception.ResourceNotFoundException;
import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.dto.TerminalCheckResponse;
import az.millikart.pbl.provider.AcquiringClient;
import az.millikart.pbl.provider.dto.TerminalCheckResult;
import az.millikart.pbl.repository.TerminalRepository;
import org.springframework.stereotype.Service;

// Кнопка «Тест» у заведённого терминала: можно ли создать на нём платёж — пробный заказ у провайдера с
// кредами компании терминала (Р-70, Р-93). Только SYSTEM_ADMIN: проверка отвечает на вопрос «подходит ли
// ключ», перебирать ключи чужим ролям незачем. Каждая проверка — в журнале: пробный заказ — внешний след.
@Service
public class TerminalCheckService {

    private final AcquiringClient acquiringClient;
    private final TerminalRepository terminalRepository;
    private final ProviderCredentialsService providerCredentials;
    private final AuditLogService auditLogService;

    public TerminalCheckService(AcquiringClient acquiringClient,
                                TerminalRepository terminalRepository,
                                ProviderCredentialsService providerCredentials,
                                AuditLogService auditLogService) {
        this.acquiringClient = acquiringClient;
        this.terminalRepository = terminalRepository;
        this.providerCredentials = providerCredentials;
        this.auditLogService = auditLogService;
    }

    public TerminalCheckResponse checkExisting(Integer terminalId, UserPrincipal principal) {
        requireSystemAdmin(principal, String.valueOf(terminalId), "terminal " + terminalId);
        Terminal terminal = terminalRepository.findById(terminalId)
                .orElseThrow(() -> new ResourceNotFoundException("Terminal not found: " + terminalId));
        TerminalCheckResult result = acquiringClient.checkOrderCreation(providerCredentials.forTerminal(terminal));
        record(String.valueOf(terminalId), terminal.getCompanyId(), principal,
                "Checked payment creation on terminal " + terminalId + " with the company credentials", result);
        return TerminalCheckResponse.of(result);
    }

    private void requireSystemAdmin(UserPrincipal principal, String entityId, String what) {
        if (UserPrincipal.getRole(principal) != Role.SYSTEM_ADMIN) {
            auditLogService.logDenied(AuditEntity.TERMINAL, entityId, AuditAction.READ,
                    UserPrincipal.getUsername(principal), UserPrincipal.getCompanyId(principal),
                    "Denied: role " + UserPrincipal.getRawRole(principal) + " attempted to check " + what);
            throw new InvalidStateException("Access denied");
        }
    }

    private void record(String entityId, String companyId, UserPrincipal principal, String what, TerminalCheckResult result) {
        String details = what + ": " + result.outcome()
                + (result.providerErrorCode() != null ? " (" + result.providerErrorCode() + ")" : "");
        auditLogService.recordSuccess(AuditEvent.of(
                AuditEntity.TERMINAL, entityId, AuditAction.READ,
                UserPrincipal.getUsername(principal), companyId, details));
    }
}
