package az.millikart.ecom.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import az.millikart.common.audit.AuditLogService;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.domain.Terminal;
import az.millikart.ecom.repository.TerminalRepository;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Из каких терминалов собирается скоуп выписки. Ошибка здесь — чужие платежи на экране или свои
// пропавшие: компания видит только свои терминалы, логин уходит в шлюз в том виде, в каком он
// лежит в login.login, а терминал без привязки к провайдеру в скоупе остаётся.
class EcomScopeServiceTest {

    private TerminalRepository terminals;
    private EcomScopeService service;

    @BeforeEach
    void setUp() {
        terminals = mock(TerminalRepository.class);
        service = new EcomScopeService(terminals, mock(AuditLogService.class));
    }

    @Test
    void aCompanySeesOnlyItsOwnTerminals() {
        List<Terminal> company = List.of(terminal("BS00001", "223456789054321"), terminal("manual_login", null));
        when(terminals.findByCompanyId("comp-01")).thenReturn(company);

        EcomScope scope = service.scopeFor(new UserPrincipal("1", "head@comp1.com", "COMPANY_HEAD", "comp-01"));

        Assertions.assertEquals(List.of("BS00001", "manual_login"), scope.logins());
        Assertions.assertEquals(List.of("223456789054321"), scope.merchantRids());
        verify(terminals, never()).findAll();
    }

    @Test
    void theAdministratorAndTheAuditorSeeEveryTerminalOfThePortal() {
        List<Terminal> all = List.of(terminal("BS00001", "M-1"), terminal("BS00002", "M-2"));
        when(terminals.findAll()).thenReturn(all);

        for (String role : List.of("SYSTEM_ADMIN", "AUDITOR")) {
            EcomScope scope = service.scopeFor(new UserPrincipal("1", "root", role, null));
            Assertions.assertEquals(List.of("BS00001", "BS00002"), scope.logins(), role);
            Assertions.assertEquals(List.of("M-1", "M-2"), scope.merchantRids(), role);
        }
    }

    // Basic-логин шлюза «TerminalSys/BS00001» в login.login лежит как «BS00001» с ownerkind TerminalSys.
    @Test
    void theOwnerPrefixOfTheBasicLoginIsDropped_andLoginsAreNotRepeated() {
        List<Terminal> company = List.of(
                terminal("TerminalSys/BS00001", "M-1"),
                terminal("BS00001", "M-1"),
                terminal("  ", null),
                terminal(null, null));
        when(terminals.findByCompanyId("comp-01")).thenReturn(company);

        EcomScope scope = service.scopeFor(new UserPrincipal("1", "head@comp1.com", "COMPANY_HEAD", "comp-01"));

        Assertions.assertEquals(List.of("BS00001"), scope.logins());
        Assertions.assertEquals(List.of("M-1"), scope.merchantRids());
    }

    @Test
    void aRoleWithoutACompanyIsRefused() {
        Assertions.assertThrows(InvalidStateException.class,
                () -> service.scopeFor(new UserPrincipal("1", "head@comp1.com", "COMPANY_HEAD", null)));
        verify(terminals, never()).findByCompanyId(anyString());
        verify(terminals, never()).findAll();
    }

    private static Terminal terminal(String login, String merchantRid) {
        Terminal terminal = mock(Terminal.class);
        when(terminal.getLogin()).thenReturn(login);
        when(terminal.getMerchantRid()).thenReturn(merchantRid);
        return terminal;
    }
}
