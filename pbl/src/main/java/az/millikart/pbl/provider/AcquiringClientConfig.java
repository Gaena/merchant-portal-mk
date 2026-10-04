package az.millikart.pbl.provider;

import az.millikart.txpg.TxpgAcquiringClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

// Клиент провайдера из txpg-client — бином с адресами pbl; сам модуль бинов не объявляет.
@Configuration
public class AcquiringClientConfig {

    // Дефолтов в @Value не заводить: выпавший ключ молча увёл бы клиента на другой хост или путь.
    // Пути дефолтятся только в application.yaml, у адресов дефолта нет нигде (P1-10).
    @Bean
    public TxpgAcquiringClient acquiringClient(
            RestClient restClient,
            @Value("${pbl.provider.api-base-url}") String apiBaseUrl,
            @Value("${pbl.provider.gateway-base-url}") String gatewayBaseUrl,
            @Value("${pbl.provider.create-order-path}") String createOrderPath,
            @Value("${pbl.provider.exec-tran-path}") String execTranPath,
            @Value("${pbl.provider.get-order-path}") String getOrderPath) {
        return new TxpgAcquiringClient(restClient, apiBaseUrl, gatewayBaseUrl, createOrderPath, execTranPath, getOrderPath);
    }
}
