package az.millikart.directory.config;

import az.millikart.common.security.CredentialCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Шифр паролей компаний к провайдеру (Р-93). Бин объявлен здесь, а не в common: ключ нужен только
// directory и pbl, и сервис без ключа не стартует (CredentialCipher, MissingSecretFailureAnalyzer).
@Configuration
public class CredentialCipherConfig {

    @Bean
    public CredentialCipher credentialCipher(@Value("${mp.credentials.encryption-key}") String key) {
        return new CredentialCipher(key);
    }
}
