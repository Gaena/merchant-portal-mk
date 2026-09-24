package az.millikart.pbl.service;

import az.millikart.common.exception.BusinessException;
import az.millikart.common.security.CredentialCipher;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.provider.ProviderCredentials;
import az.millikart.pbl.repository.CompanyCredentialsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Все запросы к провайдеру идут от имени компании терминала, а не терминала (Р-93). Компания без
// кредов — отказ до обращения к провайдеру: иначе провайдер ответил бы InvalidLogin, и отказ выглядел
// бы как его сбой.
@Service
public class ProviderCredentialsService {

    private static final Logger log = LoggerFactory.getLogger(ProviderCredentialsService.class);

    private final CompanyCredentialsRepository repository;
    private final CredentialCipher cipher;

    public ProviderCredentialsService(CompanyCredentialsRepository repository, CredentialCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    public ProviderCredentials forTerminal(Terminal terminal) {
        String companyId = terminal.getCompanyId();
        if (companyId == null) {
            log.warn("Terminal {} has no company, so there are no acquirer credentials to use", terminal.getId());
            throw new BusinessException("Terminal " + terminal.getId()
                    + " is not assigned to a company and cannot reach the acquirer");
        }
        CompanyCredentialsRepository.StoredCredentials stored = repository.findByCompanyId(companyId)
                .filter(credentials -> credentials.login() != null && !credentials.login().isBlank()
                        && credentials.encryptedPassword() != null && !credentials.encryptedPassword().isBlank())
                .orElseThrow(() -> {
                    log.warn("Company {} of terminal {} has no acquirer credentials", companyId, terminal.getId());
                    return new BusinessException("Company " + companyId
                            + " has no acquirer credentials; a system administrator must set them on the company");
                });
        return new ProviderCredentials(stored.login(), cipher.decrypt(stored.encryptedPassword()));
    }
}
