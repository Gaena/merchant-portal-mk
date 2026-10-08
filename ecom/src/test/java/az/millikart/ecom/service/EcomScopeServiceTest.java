package az.millikart.ecom.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.repository.CompanyLoginRepository;
import az.millikart.ecom.repository.ProviderLoginRepository;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Из чего собирается скоуп выписки (Р-97). Ошибка здесь — чужие платежи на экране или свои пропавшие:
// компания видит мерчантов только своего логина мультимерчанта, логин ищется в слепке без префикса, а
// компания без логина получает пустую выписку, а не всю.
class EcomScopeServiceTest {

    private CompanyLoginRepository companies;
    private ProviderLoginRepository providerLogins;
    private AuditLogService auditLogService;
    private az.millikart.ecom.repository.EmployeeMerchantRepository employeeMerchants;
    private EcomScopeService service;

    @BeforeEach
    void setUp() {
        companies = mock(CompanyLoginRepository.class);
        providerLogins = mock(ProviderLoginRepository.class);
        auditLogService = mock(AuditLogService.class);
        employeeMerchants = mock(az.millikart.ecom.repository.EmployeeMerchantRepository.class);
        service = new EcomScopeService(companies, providerLogins, auditLogService, employeeMerchants);
    }

    @Test
    void aCompanySeesTheMerchantsOfItsOwnLogin() {
        when(companies.providerLoginOf("comp-01")).thenReturn(List.of("MultiMerchantSys/bazarstore@company.com"));
        when(providerLogins.findLinkedMerchantRids(List.of("bazarstore@company.com")))
                .thenReturn(List.of("123456789054321", "223456789054323"));

        EcomScope scope = service.scopeFor(new UserPrincipal("1", "head@comp1.com", "COMPANY_HEAD", "comp-01"));

        Assertions.assertEquals(List.of("123456789054321", "223456789054323"), scope.merchantRids());
        verify(companies, never()).allProviderLogins();
    }

    @Test
    void theAdministratorAndTheAuditorSeeTheMerchantsOfEveryCompanyLogin() {
        when(companies.allProviderLogins()).thenReturn(List.of("MultiMerchantSys/a@x.az", "MultiMerchantSys/b@y.az"));
        when(providerLogins.findLinkedMerchantRids(List.of("a@x.az", "b@y.az"))).thenReturn(List.of("M-1", "M-2"));

        for (String role : List.of("SYSTEM_ADMIN", "AUDITOR")) {
            EcomScope scope = service.scopeFor(new UserPrincipal("1", "root", role, null));
            Assertions.assertEquals(List.of("M-1", "M-2"), scope.merchantRids(), role);
        }
        verify(companies, never()).providerLoginOf(anyString());
    }

    // Компания без кредов (заведена до Р-93) и логин без префикса мультимерчанта — пустой скоуп без
    // запроса к слепку: пустой список логинов не должен превратиться в «все мерчанты».
    @Test
    void aCompanyWithoutAMultimerchantLogin_hasAnEmptyScope() {
        when(companies.providerLoginOf("comp-01")).thenReturn(List.of());
        when(companies.providerLoginOf("comp-02")).thenReturn(List.of("TerminalSys/BS00001"));
        when(companies.providerLoginOf("comp-03")).thenReturn(List.of("MultiMerchantSys/"));

        for (String company : List.of("comp-01", "comp-02", "comp-03")) {
            EcomScope scope = service.scopeFor(new UserPrincipal("1", "head@comp1.com", "COMPANY_HEAD", company));
            Assertions.assertTrue(scope.merchantRids().isEmpty(), company);
        }
        verify(providerLogins, never()).findLinkedMerchantRids(any());
    }

    // Р-131: сотрудник видит только мерчантов назначенных ему терминалов — и только тех, что связаны с логином
    // его компании: назначение не расширяет скоуп компании, отвязанный мерчант уходит и у него.
    @Test
    void anEmployeeSeesOnlyTheMerchantsOfItsAssignedTerminals_withinTheCompanyLogin() {
        when(companies.providerLoginOf("comp-01")).thenReturn(List.of("MultiMerchantSys/bazarstore@company.com"));
        when(providerLogins.findLinkedMerchantRids(List.of("bazarstore@company.com")))
                .thenReturn(List.of("M-1", "M-2", "M-3"));
        when(employeeMerchants.assignedMerchantRids("u-7", "comp-01")).thenReturn(List.of("M-2", "M-9"));

        EcomScope scope = service.scopeFor(new UserPrincipal("u-7", "clerk@comp1.com", "COMPANY_EMPLOYEE", "comp-01"));

        Assertions.assertEquals(List.of("M-2"), scope.merchantRids());
    }

    // Без назначений — пустая выписка, и слепок логинов не спрашивается: пустой список не должен стать «всей
    // компанией».
    @Test
    void anEmployeeWithoutAssignments_hasAnEmptyScope() {
        when(employeeMerchants.assignedMerchantRids("u-7", "comp-01")).thenReturn(List.of());

        EcomScope scope = service.scopeFor(new UserPrincipal("u-7", "clerk@comp1.com", "COMPANY_EMPLOYEE", "comp-01"));

        Assertions.assertTrue(scope.merchantRids().isEmpty());
        verify(companies, never()).providerLoginOf(anyString());
        verify(providerLogins, never()).findLinkedMerchantRids(any());
    }

    // Отказ — с записью в журнал: роль компании без компании — сбой заведения учётки, его ищут по журналу.
    @Test
    void aRoleWithoutACompanyIsRefused() {
        Assertions.assertThrows(InvalidStateException.class,
                () -> service.scopeFor(new UserPrincipal("1", "head@comp1.com", "COMPANY_HEAD", null)));
        verify(companies, never()).providerLoginOf(anyString());
        verify(companies, never()).allProviderLogins();
        verify(auditLogService).logDenied(AuditEntity.TERMINAL, "ALL", AuditAction.LIST, "head@comp1.com", null,
                "Denied: COMPANY_HEAD without a company asked for acquiring transactions");
    }

    // Роль сверяется строго (AGENTS §6): «system_admin» — не администратор, и отказ тоже в журнале.
    @Test
    void anUnknownRoleIsRefused() {
        Assertions.assertThrows(InvalidStateException.class,
                () -> service.scopeFor(new UserPrincipal("1", "head@comp1.com", "system_admin", "comp-01")));
        verify(companies, never()).allProviderLogins();
        verify(auditLogService).logDenied(AuditEntity.TERMINAL, "ALL", AuditAction.LIST, "head@comp1.com", "comp-01",
                "Denied: unrecognised role system_admin asked for acquiring transactions");
    }
}
