package az.millikart.ecom.config;

import az.millikart.txpg.TxpgAcquiringClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// Клиент провайдера для возврата и списания заказов выписки (Р-124); сам txpg-client бинов не объявляет.
// Таймауты — как у pbl: таймаут чтения денежного вызова — это неизвестный исход, не отказ.
@Configuration
public class AcquiringClientConfig {

    @Bean
    public TxpgAcquiringClient acquiringClient(
            @Value("${ecom.provider.api-base-url}") String apiBaseUrl,
            @Value("${ecom.provider.gateway-base-url}") String gatewayBaseUrl,
            @Value("${ecom.provider.create-order-path}") String createOrderPath,
            @Value("${ecom.provider.exec-tran-path}") String execTranPath,
            @Value("${ecom.provider.get-order-path}") String getOrderPath) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(10000);
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        return new TxpgAcquiringClient(restClient, apiBaseUrl, gatewayBaseUrl, createOrderPath, execTranPath, getOrderPath);
    }
}
