package az.millikart.pbl.provider;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.provider.dto.EcomCreateOrderRequest;
import az.millikart.pbl.provider.dto.EcomCreateOrderResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnProperty(name = "pbl.provider.stub", havingValue = "false", matchIfMissing = true)
public class TxpgAcquiringClient implements AcquiringClient {

    private static final Logger log = LoggerFactory.getLogger(TxpgAcquiringClient.class);

    private final RestClient restClient;
    private final String apiBaseUrl;
    private final String gatewayBaseUrl;
    private final String createOrderPath;
    private final String execTranPath;
    private final String getOrderPath;

    public TxpgAcquiringClient(
            RestClient restClient,
            @Value("${pbl.provider.api-base-url}") String apiBaseUrl,
            @Value("${pbl.provider.gateway-base-url}") String gatewayBaseUrl,
            @Value("${pbl.provider.create-order-path:/order}") String createOrderPath,
            @Value("${pbl.provider.exec-tran-path:/api/order/{orderId}/exec-tran}") String execTranPath,
            @Value("${pbl.provider.get-order-path:/api/order/{orderId}}") String getOrderPath) {
        this.restClient = restClient;
        this.apiBaseUrl = apiBaseUrl;
        this.gatewayBaseUrl = gatewayBaseUrl;
        this.createOrderPath = createOrderPath;
        this.execTranPath = execTranPath;
        this.getOrderPath = getOrderPath;
    }

    @Override
    @CircuitBreaker(name = "acquiring")
    @Retry(name = "acquiring")
    public EcomCreateOrderResponse createEcomOrder(PaymentLink link, String login, String password, UUID merchantRid, String hppRedirectUrl) {
        String url = UriComponentsBuilder.fromUriString(gatewayBaseUrl)
                .path(createOrderPath)
                .toUriString();

        log.info("Sending order registration request to provider via RestClient. URL: {}, login: {}, merchantRid: {}", url, login, merchantRid);

        EcomCreateOrderRequest.SubMerchant subMerchant = new EcomCreateOrderRequest.SubMerchant("https://millikart.az/");

        EcomCreateOrderRequest request = new EcomCreateOrderRequest(
                new EcomCreateOrderRequest.Order(
                        "Order_SMS", // Used for both SMS and DMS payments
                        merchantRid.toString(),
                        link.getAmount(),
                        link.getCurrency(),
                        link.getDescription() != null ? link.getDescription() : "Payment via Pay-By-Link",
                        "az",
                        hppRedirectUrl,
                        subMerchant
                )
        );

        try {
            EcomCreateOrderResponse response = restClient.post()
                    .uri(url)
                    .headers(headers -> {
                        headers.setBasicAuth(login, password);
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(request)
                    .retrieve()
                    .body(EcomCreateOrderResponse.class);

            log.info("Order registration successful for merchantRid: {}", merchantRid);
            return response;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Order registration failed. Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            String desc = extractErrorDescription(e.getResponseBodyAsString());
            throw new az.millikart.common.exception.BusinessException("Acquirer error: " + desc);
        } catch (Exception e) {
            log.error("Order registration request failed.", e);
            throw new az.millikart.common.exception.BusinessException("Acquirer connection failed: " + e.getMessage());
        }
    }

    @Override
    @CircuitBreaker(name = "acquiring")
    @Retry(name = "acquiring")
    @SuppressWarnings("unchecked")
    public Map<String, Object> completeDms(String providerOrderId, String password, String login, String terminalPassword, BigDecimal amount) {
        String url = UriComponentsBuilder.fromUriString(apiBaseUrl)
                .path(execTranPath)
                .queryParam("password", password)
                .buildAndExpand(providerOrderId)
                .toUriString();

        log.info("Sending DMS complete clearing request via RestClient. URL: {}, providerOrderId: {}", url, providerOrderId);

        Map<String, Object> tran = new HashMap<>();
        tran.put("phase", "Clearing");
        Map<String, Object> body = new HashMap<>();
        body.put("tran", tran);

        try {
            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .headers(headers -> {
                        headers.setBasicAuth(login, terminalPassword);
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            log.info("DMS complete response received for providerOrderId: {}", providerOrderId);
            return response;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("DMS complete failed. Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            String desc = extractErrorDescription(e.getResponseBodyAsString());
            throw new az.millikart.common.exception.BusinessException("Acquirer error: " + desc);
        } catch (Exception e) {
            log.error("DMS complete request failed.", e);
            throw new az.millikart.common.exception.BusinessException("Clearing capture failed: " + e.getMessage());
        }
    }

    @Override
    @CircuitBreaker(name = "acquiring")
    @Retry(name = "acquiring")
    @SuppressWarnings("unchecked")
    public Map<String, Object> refund(String providerOrderId, String password, String login, String terminalPassword, BigDecimal amount) {
        String url = UriComponentsBuilder.fromUriString(apiBaseUrl)
                .path(execTranPath)
                .queryParam("password", password)
                .buildAndExpand(providerOrderId)
                .toUriString();

        log.info("Sending refund request via RestClient. URL: {}, providerOrderId: {}, amount: {}", url, providerOrderId, amount);

        Map<String, Object> tran = new HashMap<>();
        tran.put("phase", "Single");
        tran.put("amount", amount.toString());
        tran.put("type", "Refund");
        Map<String, Object> body = new HashMap<>();
        body.put("tran", tran);

        try {
            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .headers(headers -> {
                        headers.setBasicAuth(login, terminalPassword);
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            log.info("Refund request successful for providerOrderId: {}", providerOrderId);
            return response;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Refund request failed. Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            String desc = extractErrorDescription(e.getResponseBodyAsString());
            throw new az.millikart.common.exception.BusinessException("Acquirer error: " + desc);
        } catch (Exception e) {
            log.error("Refund request failed.", e);
            throw new az.millikart.common.exception.BusinessException("Refund failed: " + e.getMessage());
        }
    }

    @Override
    @CircuitBreaker(name = "acquiring")
    @Retry(name = "acquiring")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOrderStatus(String providerOrderId, String password, String login, String terminalPassword) {
        String url = UriComponentsBuilder.fromUriString(apiBaseUrl)
                .path(getOrderPath)
                .queryParam("password", password)
                .queryParam("orderDetailLevel", 2)
                .queryParam("tokenDetailLevel", 2)
                .queryParam("tranDetailLevel", 2)
                .buildAndExpand(providerOrderId)
                .toUriString();

        log.info("Sending order status check request via RestClient. URL: {}, providerOrderId: {}", url, providerOrderId);

        try {
            Map<String, Object> body = restClient.get()
                    .uri(url)
                    .headers(headers -> headers.setBasicAuth(login, terminalPassword))
                    .retrieve()
                    .body(Map.class);

            log.debug("Order status details response received for providerOrderId: {}", providerOrderId);
            if (body != null && body.containsKey("order")) {
                return (Map<String, Object>) body.get("order");
            }
            return body;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Order status check failed. Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            String desc = extractErrorDescription(e.getResponseBodyAsString());
            throw new az.millikart.common.exception.BusinessException("Acquirer error: " + desc);
        } catch (Exception e) {
            log.error("Order status check request failed.", e);
            throw new az.millikart.common.exception.BusinessException("Order status check failed: " + e.getMessage());
        }
    }

    private String extractErrorDescription(String body) {
        if (body == null || body.isBlank()) {
            return "Unknown error";
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            if (node.has("errorDescription")) {
                return node.get("errorDescription").asText();
            }
            if (node.has("message")) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {}
        return body;
    }
}
