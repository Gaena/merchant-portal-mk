package az.millikart.pbl.service;

import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.repository.EmployeeTerminalRepository;
import az.millikart.pbl.repository.TerminalRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

// Терминалы, видимые роли компании: руководителю и менеджеру — все терминалы компании, сотруднику — только
// назначенные ему (Р-131). Единственное место этого правила в pbl: списки, статистика и ворота validateAccess
// спрашивают здесь. Назначения читаются из базы на каждом запросе — снятое действует сразу, а не через 15 минут.
@Component
public class TerminalScope {

    private final TerminalRepository terminalRepository;
    private final EmployeeTerminalRepository employeeTerminals;

    public TerminalScope(TerminalRepository terminalRepository, EmployeeTerminalRepository employeeTerminals) {
        this.terminalRepository = terminalRepository;
        this.employeeTerminals = employeeTerminals;
    }

    // Глобальных читателей сюда не приводить: для них скоупа нет вовсе. Без компании — пусто, а не «всё».
    public List<Integer> companyTerminalIds(UserPrincipal principal) {
        String companyId = UserPrincipal.getCompanyId(principal);
        if (companyId == null || companyId.isBlank()) {
            return List.of();
        }
        Set<Integer> assigned = assignedOrNull(principal, companyId);
        return terminalRepository.findAllByCompanyId(companyId).stream()
                .map(Terminal::getId)
                .filter(id -> assigned == null || assigned.contains(id))
                .toList();
    }

    // Терминал своей компании, который сотруднику не назначен, — не его. Остальным ролям — true: компанию
    // проверяет вызывающий.
    public boolean allows(UserPrincipal principal, Terminal terminal) {
        Set<Integer> assigned = assignedOrNull(principal, terminal.getCompanyId());
        return assigned == null || assigned.contains(terminal.getId());
    }

    private Set<Integer> assignedOrNull(UserPrincipal principal, String companyId) {
        if (UserPrincipal.getRole(principal) != Role.COMPANY_EMPLOYEE) {
            return null;
        }
        return employeeTerminals.assignedTerminalIds(UserPrincipal.getUserId(principal), companyId);
    }
}
