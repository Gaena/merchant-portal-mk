package az.millikart.ecom.service;

import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.domain.Terminal;
import az.millikart.ecom.repository.TerminalRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Чьи платежи видит пользователь: компания → её терминалы → их логины у шлюза. Пустой список —
// пустая выписка; ветки «терминалов нет, значит показать всё» быть не должно: так выглядит показ
// мерчанту А оборотов мерчанта Б.
@Service
public class EcomScopeService {

    // Basic-логин шлюза пишется как OwnerKind/login («TerminalSys/Admin», TXPG-client-side-integration.md),
    // а в login.login схемы шлюза — без префикса: выписка ищет ownerkind = 'TerminalSys' и login.
    static final String TERMINAL_OWNER_PREFIX = "TerminalSys/";

    private final TerminalRepository terminals;
    private final AuditLogService auditLogService;

    public EcomScopeService(TerminalRepository terminals, AuditLogService auditLogService) {
        this.terminals = terminals;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public EcomScope scopeFor(UserPrincipal principal) {
        Role role = UserPrincipal.getRole(principal);
        if (role == null) {
            throw new InvalidStateException("Access denied");
        }

        // SYSTEM_ADMIN и AUDITOR читают глобально — но и они видят только то, что заведено
        // у нас: терминал провайдера, за которым не стоит наш, к порталу отношения не имеет.
        if (role == Role.SYSTEM_ADMIN || role == Role.AUDITOR) {
            return scopeOf(terminals.findAll());
        }

        String companyId = UserPrincipal.getCompanyId(principal);
        if (companyId == null) {
            auditLogService.logDenied(AuditEntity.TERMINAL, "ALL", AuditAction.LIST,
                    UserPrincipal.getUsername(principal), null,
                    "Denied: " + role + " without a company asked for acquiring transactions");
            throw new InvalidStateException("Access denied: User not assigned to a company");
        }
        return scopeOf(terminals.findByCompanyId(companyId));
    }

    private static EcomScope scopeOf(List<Terminal> scoped) {
        List<String> logins = scoped.stream()
                .map(Terminal::getLogin)
                .map(EcomScopeService::gatewayLogin)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<String> merchantRids = scoped.stream()
                .map(Terminal::getMerchantRid)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return new EcomScope(logins, merchantRids);
    }

    static String gatewayLogin(String login) {
        if (login == null) {
            return null;
        }
        String value = login.trim();
        if (value.startsWith(TERMINAL_OWNER_PREFIX)) {
            value = value.substring(TERMINAL_OWNER_PREFIX.length());
        }
        return value.isEmpty() ? null : value;
    }
}
