package az.millikart.pbl.service;

import az.millikart.common.exception.BusinessException;
import az.millikart.common.security.CredentialCipher;
import az.millikart.pbl.domain.Terminal;
import az.millikart.txpg.ProviderCredentials;
import az.millikart.pbl.repository.CompanyCredentialsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Запросы к провайдеру — от имени компании терминала, не терминала (Р-93). Нет кредов — отказ до
// провайдера: его InvalidLogin выглядел бы сбоем провайдера.
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

    // Без номера терминала провайдер заказ не примет — отказ до него (Р-96). Номера нет у терминалов,
    // заведённых до Р-96 без справочника.
    public String terminalRidOf(Terminal terminal) {
        String terminalRid = terminal.getTerminalRid();
        if (terminalRid == null || terminalRid.isBlank()) {
            log.warn("Terminal {} has no provider terminal number", terminal.getId());
            throw new BusinessException("Terminal " + terminal.getId()
                    + " has no provider terminal number and cannot take payments; link it from the provider directory");
        }
        return terminalRid;
    }
}
