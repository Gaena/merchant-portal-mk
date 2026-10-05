package az.millikart.ecom.config;

import az.millikart.common.security.CredentialCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Шифр паролей компаний к провайдеру (Р-93): ecom расшифровывает пароль компании терминала для возврата и
// списания заказа выписки (Р-124). Без ключа сервис не стартует.
@Configuration
public class CredentialCipherConfig {

    @Bean
    public CredentialCipher credentialCipher(@Value("${mp.credentials.encryption-key}") String key) {
        return new CredentialCipher(key);
    }
}
