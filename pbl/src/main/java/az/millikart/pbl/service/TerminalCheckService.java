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

/**
 * Проверка учётных данных терминала у провайдера.
 *
 * **Только SYSTEM_ADMIN** — те же ворота, что у чтения и смены пароля терминала: проверка
 * пароля отвечает на вопрос «подходит ли этот ключ», и перебирать ключи чужим ролям незачем.
 *
 * Два режима. Для терминала, который ещё заводят, учётные данные приходят в запросе; для уже
 * заведённого берутся из базы по номеру, и пароль при этом наружу не уходит вовсе — ответ
 * говорит только, подошёл ли он.
 *
 * Каждая проверка ложится в журнал аудита вместе с исходом. Пробный заказ у провайдера — это
 * внешний след, и в нашем журнале должно быть видно, кто и когда его оставил. Пароль в запись
 * не попадает.
 */
@Service
public class TerminalCheckService {

    private final AcquiringClient acquiringClient;
    private final TerminalRepository terminalRepository;
    private final AuditLogService auditLogService;

    public TerminalCheckService(AcquiringClient acquiringClient,
                                TerminalRepository terminalRepository,
                                AuditLogService auditLogService) {
        this.acquiringClient = acquiringClient;
        this.terminalRepository = terminalRepository;
        this.auditLogService = auditLogService;
    }

    public TerminalCheckResponse checkNew(String login, String password, UserPrincipal principal) {
        requireSystemAdmin(principal, "NEW", "credentials for login " + login);
        TerminalCheckResult result = acquiringClient.checkTerminalCredentials(login, password);
        record("NEW", null, principal, "Checked acquiring credentials for login " + login, result);
        return TerminalCheckResponse.of(result);
    }

    public TerminalCheckResponse checkExisting(Integer terminalId, UserPrincipal principal) {
        requireSystemAdmin(principal, String.valueOf(terminalId), "terminal " + terminalId);
        Terminal terminal = terminalRepository.findById(terminalId)
                .orElseThrow(() -> new ResourceNotFoundException("Terminal not found: " + terminalId));
        TerminalCheckResult result = acquiringClient.checkTerminalCredentials(terminal.getLogin(), terminal.getPassword());
        record(String.valueOf(terminalId), terminal.getCompanyId(), principal,
                "Checked acquiring credentials of terminal " + terminalId, result);
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
