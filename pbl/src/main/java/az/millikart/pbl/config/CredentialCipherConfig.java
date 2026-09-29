package az.millikart.pbl.config;

import az.millikart.common.security.CredentialCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Шифр паролей компаний к провайдеру (Р-93). Бин здесь, а не в common: без ключа сервис не стартует,
// а ключ есть только у directory и pbl.
@Configuration
public class CredentialCipherConfig {

    @Bean
    public CredentialCipher credentialCipher(@Value("${mp.credentials.encryption-key}") String key) {
        return new CredentialCipher(key);
    }
}
