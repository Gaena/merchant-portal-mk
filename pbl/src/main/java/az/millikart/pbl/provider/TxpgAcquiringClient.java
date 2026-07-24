package az.millikart.pbl.provider;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentType;
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
            @Value("${pbl.provider.api-base-url:https://test.millikart.az:8083}") String apiBaseUrl,
            @Value("${pbl.provider.gateway-base-url:https://test.millikart.az:8083}") String gatewayBaseUrl,
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

        String typeRid = (link.getPaymentType() == PaymentType.DMS) ? "Order_DMS" : "Order_SMS";

        EcomCreateOrderRequest.SubMerchant subMerchant = new EcomCreateOrderRequest.SubMerchant("https://millikart.az/");

        EcomCreateOrderRequest request = new EcomCreateOrderRequest(
                new EcomCreateOrderRequest.Order(
                        typeRid,
                        merchantRid.toString(),
                        link.getAmount(),
                        link.getCurrency(),
                        link.getDescription() != null ? link.getDescription() : "Payment via Pay-By-Link",
                        "az",
                        hppRedirectUrl,
                        subMerchant
                )
        );

        log.info("PROVIDER REQ [createEcomOrder] -> POST URL: {}, Login: {}, MerchantRid: {}, Type: {}, Amount: {} {}", 
                url, login, merchantRid, typeRid, link.getAmount(), link.getCurrency());
        log.debug("PROVIDER REQ BODY [createEcomOrder]: {}", request);

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

            log.info("PROVIDER RESP [createEcomOrder] <- SUCCESS for MerchantRid: {}, ProviderOrderId: {}", 
                    merchantRid, response != null && response.order() != null ? response.order().id() : "N/A");
            log.debug("PROVIDER RESP BODY [createEcomOrder]: {}", response);
            return response;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("PROVIDER RESP [createEcomOrder] <- FAILED. HTTP Status: {}, Error Body: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            String desc = extractErrorDescription(e.getResponseBodyAsString());
            throw new az.millikart.common.exception.BusinessException("Acquirer error: " + desc);
        } catch (Exception e) {
            log.error("PROVIDER REQ [createEcomOrder] <- CONNECTION EXCEPTION: {}", e.getMessage(), e);
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

        Map<String, Object> tran = new HashMap<>();
        tran.put("phase", "Clearing");
        Map<String, Object> body = new HashMap<>();
        body.put("tran", tran);

        log.info("PROVIDER REQ [completeDms] -> POST URL: {}, ProviderOrderId: {}, Login: {}, Amount: {}", url, providerOrderId, login, amount);
        log.debug("PROVIDER REQ BODY [completeDms]: {}", body);

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

            log.info("PROVIDER RESP [completeDms] <- SUCCESS for ProviderOrderId: {}, Response: {}", providerOrderId, response);
            checkAndThrowIfErrorCode(response, "completeDms");
            return response;
        } catch (az.millikart.common.exception.BusinessException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("PROVIDER RESP [completeDms] <- FAILED. HTTP Status: {}, Error Body: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            String desc = extractErrorDescription(e.getResponseBodyAsString());
            throw new az.millikart.common.exception.BusinessException("Acquirer error: " + desc);
        } catch (Exception e) {
            log.error("PROVIDER REQ [completeDms] <- CONNECTION EXCEPTION: {}", e.getMessage(), e);
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

        Map<String, Object> tran = new HashMap<>();
        tran.put("phase", "Single");
        tran.put("amount", amount.toString());
        tran.put("type", "Refund");
        Map<String, Object> body = new HashMap<>();
        body.put("tran", tran);

        log.info("PROVIDER REQ [refund] -> POST URL: {}, ProviderOrderId: {}, Login: {}, Refund Amount: {}", url, providerOrderId, login, amount);
        log.debug("PROVIDER REQ BODY [refund]: {}", body);

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

            log.info("PROVIDER RESP [refund] <- SUCCESS for ProviderOrderId: {}, Response: {}", providerOrderId, response);
            checkAndThrowIfErrorCode(response, "refund");
            return response;
        } catch (az.millikart.common.exception.BusinessException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("PROVIDER RESP [refund] <- FAILED. HTTP Status: {}, Error Body: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            String desc = extractErrorDescription(e.getResponseBodyAsString());
            throw new az.millikart.common.exception.BusinessException("Acquirer error: " + desc);
        } catch (Exception e) {
            log.error("PROVIDER REQ [refund] <- CONNECTION EXCEPTION: {}", e.getMessage(), e);
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

        log.info("PROVIDER REQ [getOrderStatus] -> GET URL: {}, ProviderOrderId: {}, Login: {}", url, providerOrderId, login);

        try {
            Map<String, Object> body = restClient.get()
                    .uri(url)
                    .headers(headers -> headers.setBasicAuth(login, terminalPassword))
                    .retrieve()
                    .body(Map.class);

            log.info("PROVIDER RESP [getOrderStatus] <- SUCCESS for ProviderOrderId: {}, Response: {}", providerOrderId, body);
            checkAndThrowIfErrorCode(body, "getOrderStatus");
            if (body != null && body.containsKey("order")) {
                return (Map<String, Object>) body.get("order");
            }
            return body;
        } catch (az.millikart.common.exception.BusinessException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("PROVIDER RESP [getOrderStatus] <- FAILED. HTTP Status: {}, Error Body: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            String desc = extractErrorDescription(e.getResponseBodyAsString());
            throw new az.millikart.common.exception.BusinessException("Acquirer error: " + desc);
        } catch (Exception e) {
            log.error("PROVIDER REQ [getOrderStatus] <- CONNECTION EXCEPTION: {}", e.getMessage(), e);
            throw new az.millikart.common.exception.BusinessException("Order status check failed: " + e.getMessage());
        }
    }

    private void checkAndThrowIfErrorCode(Map<String, Object> response, String action) {
        if (response != null && response.containsKey("errorCode")) {
            String errorCode = String.valueOf(response.get("errorCode"));
            String errorDesc = response.containsKey("errorDescription") && response.get("errorDescription") != null
                    ? String.valueOf(response.get("errorDescription"))
                    : errorCode;
            log.error("PROVIDER RESP [{}] <- REJECTED BY MILLIKART. ErrorCode: {}, Description: {}", action, errorCode, errorDesc);
            throw new az.millikart.common.exception.BusinessException("Acquirer error: " + errorDesc);
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
