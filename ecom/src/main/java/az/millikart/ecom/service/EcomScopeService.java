package az.millikart.ecom.service;

import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.repository.TerminalRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Чьи платежи вправе видеть этот пользователь.
 *
 * Скоуп строится по цепочке «компания → её терминалы → терминал провайдера за каждым». У
 * провайдера один терминал это один мерчант, поэтому отдельного справочника связей не нужно:
 * он дублировал бы таблицу терминалов и однажды с ней разъехался.
 *
 * Пустой список означает пустую выписку. Ветки «терминалов нет, значит показать всё» здесь нет
 * и заводить её нельзя: так выглядит показ мерчанту А оборотов мерчанта Б.
 */
@Service
public class EcomScopeService {

    private final TerminalRepository terminals;
    private final AuditLogService auditLogService;

    public EcomScopeService(TerminalRepository terminals, AuditLogService auditLogService) {
        this.terminals = terminals;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<String> merchantRidsFor(UserPrincipal principal) {
        Role role = UserPrincipal.getRole(principal);
        if (role == null) {
            throw new InvalidStateException("Access denied");
        }

        // SYSTEM_ADMIN и AUDITOR читают глобально — но и они видят только то, что заведено
        // у нас: терминал провайдера, за которым не стоит наш, к порталу отношения не имеет.
        if (role == Role.SYSTEM_ADMIN || role == Role.AUDITOR) {
            return terminals.findAllProviderRids();
        }

        String companyId = UserPrincipal.getCompanyId(principal);
        if (companyId == null) {
            auditLogService.logDenied(AuditEntity.TERMINAL, "ALL", AuditAction.LIST,
                    UserPrincipal.getUsername(principal), null,
                    "Denied: " + role + " without a company asked for acquiring transactions");
            throw new InvalidStateException("Access denied: User not assigned to a company");
        }
        return terminals.findProviderRidsByCompany(companyId);
    }
}
