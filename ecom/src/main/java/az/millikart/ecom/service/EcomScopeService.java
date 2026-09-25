package az.millikart.ecom.service;

import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.repository.CompanyLoginRepository;
import az.millikart.ecom.repository.ProviderLoginRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Чьи платежи видит пользователь: компания → её логин мультимерчанта → его мерчанты в слепке
// provider_logins (Р-97). Пустой список — пустая выписка; ветки «мерчантов нет, значит показать всё»
// быть не должно: так выглядит показ мерчанту А оборотов мерчанта Б.
@Service
public class EcomScopeService {

    // Логин компании хранится с префиксом (Р-93), в слепке — без него, как в login.login шлюза.
    static final String MULTI_MERCHANT_PREFIX = "MultiMerchantSys/";

    private final CompanyLoginRepository companies;
    private final ProviderLoginRepository providerLogins;
    private final AuditLogService auditLogService;

    public EcomScopeService(CompanyLoginRepository companies, ProviderLoginRepository providerLogins,
                            AuditLogService auditLogService) {
        this.companies = companies;
        this.providerLogins = providerLogins;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public EcomScope scopeFor(UserPrincipal principal) {
        Role role = UserPrincipal.getRole(principal);
        if (role == null) {
            throw new InvalidStateException("Access denied");
        }

        // SYSTEM_ADMIN и AUDITOR читают глобально — но и они видят только мерчантов наших компаний:
        // мерчант провайдера, за которым не стоит логин нашей компании, к порталу отношения не имеет.
        if (role == Role.SYSTEM_ADMIN || role == Role.AUDITOR) {
            return scopeOf(companies.allProviderLogins());
        }

        String companyId = UserPrincipal.getCompanyId(principal);
        if (companyId == null) {
            auditLogService.logDenied(AuditEntity.TERMINAL, "ALL", AuditAction.LIST,
                    UserPrincipal.getUsername(principal), null,
                    "Denied: " + role + " without a company asked for acquiring transactions");
            throw new InvalidStateException("Access denied: User not assigned to a company");
        }
        return scopeOf(companies.providerLoginOf(companyId));
    }

    private EcomScope scopeOf(List<String> companyLogins) {
        List<String> logins = companyLogins.stream()
                .map(EcomScopeService::snapshotLogin)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return new EcomScope(logins.isEmpty() ? List.of() : providerLogins.findLinkedMerchantRids(logins));
    }

    // Логин без префикса — не логин мультимерчанта: его мерчантов в слепке нет, и искать их не по чему.
    static String snapshotLogin(String login) {
        if (login == null || !login.startsWith(MULTI_MERCHANT_PREFIX)) {
            return null;
        }
        String value = login.substring(MULTI_MERCHANT_PREFIX.length());
        return value.isBlank() ? null : value;
    }
}
