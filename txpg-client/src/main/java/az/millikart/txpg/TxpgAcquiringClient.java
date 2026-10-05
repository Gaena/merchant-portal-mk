package az.millikart.txpg;

import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.PaymentOutcomeUnknownException;
import az.millikart.txpg.dto.EcomCreateOrderRequest;
import az.millikart.txpg.dto.EcomCreateOrderResponse;
import az.millikart.txpg.dto.MoneyOperationResult;
import az.millikart.txpg.dto.NewOrder;
import az.millikart.txpg.dto.TerminalCheckResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Единственная реализация AcquiringClient. Второй (стаба) в main не заводить: он ответил бы
// «оплачено», не спросив эквайера. Тестовый двойник — в testFixtures (AGENTS.md §11). Не @Component:
// сервисы сканируют az.millikart целиком, и бин появился бы у каждого, кто подключил модуль; объявляет его
// сервис, который ходит к провайдеру, со своими адресами (pbl — AcquiringClientConfig).
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
            String apiBaseUrl,
            String gatewayBaseUrl,
            String createOrderPath,
            String execTranPath,
            String getOrderPath) {
        this.restClient = restClient;
        this.apiBaseUrl = apiBaseUrl;
        this.gatewayBaseUrl = gatewayBaseUrl;
        this.createOrderPath = createOrderPath;
        this.execTranPath = execTranPath;
        this.getOrderPath = getOrderPath;
    }

    // @Retry здесь безопасен, в отличие от денежных операций: дубль заказа остаётся неоплаченным (P0-7).
    @Override
    @CircuitBreaker(name = "acquiring")
    @Retry(name = "acquiring")
    public EcomCreateOrderResponse createEcomOrder(NewOrder order, ProviderCredentials credentials, String terminalRid,
                                                   UUID ridByMerchant, String hppRedirectUrl) {
        String url = UriComponentsBuilder.fromUriString(gatewayBaseUrl)
                .path(createOrderPath)
                .queryParam("terminalRid", terminalRid)
                .toUriString();

        String typeRid = order.dms() ? "Order_DMS" : "Order_SMS";

        EcomCreateOrderRequest.SubMerchant subMerchant = new EcomCreateOrderRequest.SubMerchant("https://millikart.az/");

        EcomCreateOrderRequest request = new EcomCreateOrderRequest(
                new EcomCreateOrderRequest.Order(
                        typeRid,
                        ridByMerchant.toString(),
                        order.amount(),
                        order.currency(),
                        order.description(),
                        "az",
                        hppRedirectUrl,
                        subMerchant,
                        order.payer()
                )
        );

        // Открытие описывает одна INFO-строка OpenLinkService; здесь — только DEBUG.
        log.debug("PROVIDER REQ [createEcomOrder] -> POST URL: {}, Login: {}, TerminalRid: {}, RidByMerchant: {}, Type: {}, Amount: {} {}",
                ProviderPayloads.urlForLog(url), credentials.login(), terminalRid, ridByMerchant, typeRid,
                order.amount(), order.currency());
        log.debug("PROVIDER REQ BODY [createEcomOrder]: {}", request);

        try {
            EcomCreateOrderResponse response = restClient.post()
                    .uri(url)
                    .headers(headers -> {
                        headers.setBasicAuth(credentials.login(), credentials.password());
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(request)
                    .retrieve()
                    .body(EcomCreateOrderResponse.class);

            log.debug("PROVIDER RESP [createEcomOrder] <- SUCCESS for RidByMerchant: {}, ProviderOrderId: {}",
                    ridByMerchant, response != null && response.order() != null ? response.order().id() : "N/A");
            // P0-9: в теле — пароль заказа; его маскирует EcomCreateOrderResponse.Order.toString().
            log.debug("PROVIDER RESP BODY [createEcomOrder]: {}", response);
            return response;
        } catch (HttpStatusCodeException e) {
            // 4xx — ожидаемый отказ шлюза: WARN без стектрейса (Р-98). 5xx — сбой шлюза.
            if (e.getStatusCode().is4xxClientError()) {
                log.warn("PROVIDER RESP [createEcomOrder] <- REJECTED. HTTP Status: {}, Error Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            } else {
                log.error("PROVIDER RESP [createEcomOrder] <- FAILED. HTTP Status: {}, Error Body: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            }
            throw acquirerError(e);
        } catch (Exception e) {
            log.error("PROVIDER REQ [createEcomOrder] <- CONNECTION EXCEPTION: {}", e.getMessage(), e);
            throw new BusinessException("Acquirer connection failed: " + e.getMessage());
        }
    }

    // Без @Retry (P0-7): на таймауте capture мог уже пройти, и повтор спишет дважды. Circuit breaker
    // безопасен: он отказывает в новых вызовах, но не пересылает отправленный.
    @Override
    @CircuitBreaker(name = "acquiring")
    @SuppressWarnings("unchecked")
    public MoneyOperationResult completeDms(String providerOrderId, ProviderCredentials credentials, BigDecimal amount) {
        String url = UriComponentsBuilder.fromUriString(apiBaseUrl)
                .path(execTranPath)
                .buildAndExpand(providerOrderId)
                .toUriString();

        Map<String, Object> tran = new HashMap<>();
        tran.put("phase", "Clearing");
        // Без amount эквайер спишет весь холд (P0-8); phase Clearing его принимает — подтвердил MilliKart.
        tran.put("amount", formatAmount(amount));
        Map<String, Object> body = new HashMap<>();
        body.put("tran", tran);

        log.info("PROVIDER REQ [completeDms] -> POST URL: {}, ProviderOrderId: {}, Login: {}, Amount: {}",
                ProviderPayloads.urlForLog(url), providerOrderId, credentials.login(), amount);
        log.debug("PROVIDER REQ BODY [completeDms]: {}", body);

        try {
            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .headers(headers -> {
                        headers.setBasicAuth(credentials.login(), credentials.password());
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            log.debug("PROVIDER RESP BODY [completeDms] for ProviderOrderId: {}: {}",
                    providerOrderId, ProviderPayloads.withoutSecrets(response));
            checkAndThrowIfErrorCode(response, "completeDms");
            return requireConfirmation("completeDms", providerOrderId, response);
        } catch (Exception e) {
            throw classifyMoneyOperationFailure("completeDms", providerOrderId, e);
        }
    }

    // Без @Retry (P0-7): повтор после таймаута — двойной возврат.
    @Override
    @CircuitBreaker(name = "acquiring")
    @SuppressWarnings("unchecked")
    public MoneyOperationResult refund(String providerOrderId, ProviderCredentials credentials, BigDecimal amount) {
        String url = UriComponentsBuilder.fromUriString(apiBaseUrl)
                .path(execTranPath)
                .buildAndExpand(providerOrderId)
                .toUriString();

        Map<String, Object> tran = new HashMap<>();
        tran.put("phase", "Single");
        tran.put("amount", formatAmount(amount));
        tran.put("type", "Refund");
        Map<String, Object> body = new HashMap<>();
        body.put("tran", tran);

        log.info("PROVIDER REQ [refund] -> POST URL: {}, ProviderOrderId: {}, Login: {}, Refund Amount: {}",
                ProviderPayloads.urlForLog(url), providerOrderId, credentials.login(), amount);
        log.debug("PROVIDER REQ BODY [refund]: {}", body);

        try {
            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .headers(headers -> {
                        headers.setBasicAuth(credentials.login(), credentials.password());
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            log.debug("PROVIDER RESP BODY [refund] for ProviderOrderId: {}: {}",
                    providerOrderId, ProviderPayloads.withoutSecrets(response));
            checkAndThrowIfErrorCode(response, "refund");
            return requireConfirmation("refund", providerOrderId, response);
        } catch (Exception e) {
            throw classifyMoneyOperationFailure("refund", providerOrderId, e);
        }
    }

    @Override
    @CircuitBreaker(name = "acquiring")
    @Retry(name = "acquiring")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOrderStatus(String providerOrderId, String password, ProviderCredentials credentials) {
        String url = UriComponentsBuilder.fromUriString(apiBaseUrl)
                .path(getOrderPath)
                .queryParam("password", password)
                .queryParam("orderDetailLevel", 2)
                .queryParam("tokenDetailLevel", 2)
                .queryParam("tranDetailLevel", 2)
                .buildAndExpand(providerOrderId)
                .toUriString();

        // Сверка опрашивает статус каждые 2 минуты: запрос и тело — только DEBUG.
        log.debug("PROVIDER REQ [getOrderStatus] -> GET URL: {}, ProviderOrderId: {}, Login: {}",
                ProviderPayloads.urlForLog(url), providerOrderId, credentials.login());

        try {
            Map<String, Object> body = restClient.get()
                    .uri(url)
                    .headers(headers -> headers.setBasicAuth(credentials.login(), credentials.password()))
                    .retrieve()
                    .body(Map.class);

            checkAndThrowIfErrorCode(body, "getOrderStatus");
            Map<String, Object> order = body != null && body.containsKey("order")
                    ? (Map<String, Object>) body.get("order")
                    : body;
            // P0-9: при orderDetailLevel=2 в order лежит пароль заказа (§5.8.3) — в лог только без него.
            // Лог после проверки errorCode: иначе отказ сначала объявится как SUCCESS.
            log.debug("PROVIDER RESP [getOrderStatus] <- SUCCESS for ProviderOrderId: {}, Response: {}",
                    providerOrderId, ProviderPayloads.withoutSecrets(order));
            return order;
        } catch (BusinessException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            // Без стектрейса: @Retry повторяет запрос, и стектрейс на каждую попытку — шум (Р-98).
            log.warn("PROVIDER RESP [getOrderStatus] <- FAILED for ProviderOrderId: {}. HTTP Status: {}, Error Body: {}",
                    providerOrderId, e.getStatusCode(), e.getResponseBodyAsString());
            throw acquirerError(e);
        } catch (Exception e) {
            log.warn("PROVIDER REQ [getOrderStatus] <- no answer for ProviderOrderId: {}: {}", providerOrderId, e.getMessage());
            throw new BusinessException("Order status check failed: " + e.getMessage());
        }
    }

    // Ответ провайдера и на неверный логин, и на неверный пароль компании (Р-93).
    private static final String INVALID_LOGIN = "InvalidLogin";

    // Не списывается: пробный заказ остаётся неоплаченным и уходит в Expired. Манат, а не копейка —
    // чтобы проверка не упёрлась в минимальную сумму.
    private static final BigDecimal CHECK_AMOUNT = new BigDecimal("1.00");

    // Кнопка «Тест» (Р-70, Р-93). Без @Retry и @CircuitBreaker: повтор множит пробные заказы, а общий
    // с платежами breaker закрыл бы приём платежей всем. Исходы — TerminalCheckResult (Р-103);
    // InvalidLogin в теле читается одинаково в 2xx и в 4xx/5xx.
    @Override
    public TerminalCheckResult checkOrderCreation(ProviderCredentials credentials, String terminalRid) {
        String login = credentials.login();
        String url = UriComponentsBuilder.fromUriString(gatewayBaseUrl)
                .path(createOrderPath)
                .queryParam("terminalRid", terminalRid)
                .toUriString();

        EcomCreateOrderRequest request = new EcomCreateOrderRequest(
                new EcomCreateOrderRequest.Order(
                        "Order_SMS",
                        UUID.randomUUID().toString(),
                        CHECK_AMOUNT,
                        "AZN",
                        "Terminal credentials check",
                        "az",
                        gatewayBaseUrl,
                        new EcomCreateOrderRequest.SubMerchant("https://millikart.az/"),
                        null
                )
        );

        log.info("PROVIDER REQ [checkOrderCreation] -> POST URL: {}, Login: {}",
                ProviderPayloads.urlForLog(url), login);

        try {
            Map<String, Object> body = restClient.post()
                    .uri(url)
                    .headers(headers -> {
                        headers.setBasicAuth(login, credentials.password());
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            return classifyCheck(login, body, null);
        } catch (HttpStatusCodeException e) {
            String raw = e.getResponseBodyAsString();
            if (raw == null || raw.isBlank()) {
                if (e.getStatusCode().is5xxServerError()) {
                    log.warn("PROVIDER RESP [checkOrderCreation] <- HTTP {} with an empty body for Login: {}", e.getStatusCode(), login);
                    return TerminalCheckResult.unreachable("Acquirer answered HTTP " + e.getStatusCode().value() + " with an empty body");
                }
                return notCreated(login, e.getStatusCode().value(), null);
            }
            return classifyCheck(login, parseBody(raw), e.getStatusCode().value());
        } catch (Exception e) {
            log.warn("PROVIDER REQ [checkOrderCreation] <- no answer for Login: {}: {}", login, e.getMessage());
            return TerminalCheckResult.unreachable("No answer from the acquirer: " + e.getMessage());
        }
    }

    // httpStatus — код ответа об ошибке, null у 2xx. Тело не JSON приходит сюда как null: сервер ответил,
    // значит, доступен, а заказа нет.
    private TerminalCheckResult classifyCheck(String login, Map<String, Object> body, Integer httpStatus) {
        Object errorCode = body == null ? null : body.get("errorCode");
        if (errorCode != null) {
            String code = String.valueOf(errorCode);
            String description = body.get("errorDescription") != null ? String.valueOf(body.get("errorDescription")) : code;
            if (INVALID_LOGIN.equals(code)) {
                log.info("PROVIDER RESP [checkOrderCreation] <- invalid credentials for Login: {}", login);
                return new TerminalCheckResult(TerminalCheckResult.Outcome.INVALID_CREDENTIALS, code, description);
            }
            log.info("PROVIDER RESP [checkOrderCreation] <- rejected for Login: {} with {}: {}", login, code, description);
            return new TerminalCheckResult(TerminalCheckResult.Outcome.REJECTED, code, description);
        }
        if (httpStatus == null && createdOrderId(body) != null) {
            log.info("PROVIDER RESP [checkOrderCreation] <- OK for Login: {}", login);
            return TerminalCheckResult.ok();
        }
        return notCreated(login, httpStatus, body);
    }

    private TerminalCheckResult notCreated(String login, Integer httpStatus, Map<String, Object> body) {
        Object message = body == null ? null : body.get("message");
        String description = message != null ? String.valueOf(message)
                : httpStatus != null ? "Acquirer answered HTTP " + httpStatus : "Acquirer answered without an order";
        String code = httpStatus != null ? "HTTP " + httpStatus : null;
        log.warn("PROVIDER RESP [checkOrderCreation] <- no order for Login: {}: {}", login, description);
        return new TerminalCheckResult(TerminalCheckResult.Outcome.REJECTED, code, description);
    }

    private static Object createdOrderId(Map<String, Object> body) {
        return body != null && body.get("order") instanceof Map<?, ?> order ? order.get("id") : null;
    }

    private static Map<String, Object> parseBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    // Строкой, как в примере Refund эквайера. toPlainString: toString при некоторых scale даёт "1E+3".
    // UNNECESSARY намеренно — молча не округлять: три знака отсекает PaymentLinkService, здесь они — баг.
    private static String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    // Успех подтверждает только tran.match.ridByPmo (§5.5-5.7, P1-8b), а не отсутствие errorCode. Без
    // него исход неизвестен (Р-23): 502, а не 400. Разбор защитный: кривая форма даёт «не подтверждено»,
    // а не ClassCastException.
    private MoneyOperationResult requireConfirmation(String action, String providerOrderId, Map<String, Object> response) {
        Map<String, Object> tran = asMap(response != null ? response.get("tran") : null);
        Map<String, Object> match = asMap(tran != null ? tran.get("match") : null);
        // Только ProviderPayloads.scalarText — второй разбор не заводить (AGENTS.md §10).
        String ridByPmo = ProviderPayloads.scalarText(match != null ? match.get("ridByPmo") : null);

        if (ridByPmo == null) {
            // Тело целиком намеренно (Р-23): если шлюз ответит не по §5.5-5.7, возвраты встанут, и по
            // одной строке должно быть видно, чем ответ отличается.
            log.error("PROVIDER RESP [{}] <- NO CONFIRMATION for ProviderOrderId: {}. "
                            + "The response carries no tran.match.ridByPmo. Full body: {}",
                    action, providerOrderId, ProviderPayloads.withoutSecrets(response));
            throw new PaymentOutcomeUnknownException(
                    "Acquirer accepted the " + action + " but did not confirm it: the response has no "
                            + "tran.match.ridByPmo, so the operation may or may not have executed. "
                            + "Expected shape — see project_docs/external/TXPG-client-side-integration.md §5.5-5.7.");
        }

        String approvalCode = ProviderPayloads.scalarText(tran.get("approvalCode"));
        String tranActionId = ProviderPayloads.scalarText(match.get("tranActionId"));
        if (approvalCode == null) {
            log.warn("PROVIDER RESP [{}] <- confirmed for ProviderOrderId: {} (ridByPmo {}) but without "
                    + "tran.approvalCode; the operation counts, the dispute trail is thinner", action, providerOrderId, ridByPmo);
        }
        if (tranActionId == null) {
            log.warn("PROVIDER RESP [{}] <- confirmed for ProviderOrderId: {} (ridByPmo {}) but without "
                    + "tran.match.tranActionId; the operation counts, the dispute trail is thinner", action, providerOrderId, ridByPmo);
        }
        log.info("PROVIDER RESP [{}] <- CONFIRMED for ProviderOrderId: {}. ridByPmo: {}, tranActionId: {}, approvalCode: {}",
                action, providerOrderId, ridByPmo, tranActionId, approvalCode);
        return new MoneyOperationResult(approvalCode, tranActionId, ridByPmo, response);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    // Отказ шлюза (повтор безопасен) — только errorCode в 200 и 4xx; всё прочее, включая 5xx и таймаут,
    // — неизвестность. Дефолт «неизвестно» намеренно: неизвестность, принятая за отказ, повторяют,
    // и возврат становится двойным (AGENTS.md §7).
    private RuntimeException classifyMoneyOperationFailure(String action, String providerOrderId, Exception e) {
        if (e instanceof BusinessException businessException) {
            // 200 с errorCode (checkAndThrowIfErrorCode): шлюз отказал, операция точно не выполнена.
            return businessException;
        }
        if (e instanceof PaymentOutcomeUnknownException unconfirmed) {
            // 200 без ridByPmo (requireConfirmation): тип и текст уже нужные — не оборачивать.
            return unconfirmed;
        }
        if (e instanceof HttpStatusCodeException httpError) {
            String desc = extractErrorDescription(httpError.getResponseBodyAsString());
            if (httpError.getStatusCode().is4xxClientError()) {
                log.warn("PROVIDER RESP [{}] <- REJECTED for ProviderOrderId: {}. HTTP Status: {}, Error Body: {}",
                        action, providerOrderId, httpError.getStatusCode(), httpError.getResponseBodyAsString());
                return new AcquirerDeclinedException("Acquirer error: " + desc);
            }
            // 5xx: шлюз принял запрос и упал за ним. Без стектрейса — его напечатает GlobalExceptionHandler
            // под маркером PAYMENT_OUTCOME_UNKNOWN.
            log.error("PROVIDER RESP [{}] <- OUTCOME UNKNOWN for ProviderOrderId: {}. HTTP Status: {}, Error Body: {}",
                    action, providerOrderId, httpError.getStatusCode(), httpError.getResponseBodyAsString());
            return new PaymentOutcomeUnknownException(
                    "Acquirer did not confirm the " + action + " (HTTP " + httpError.getStatusCode() + "): " + desc, httpError);
        }
        if (e instanceof ResourceAccessException) {
            // Таймаут или обрыв: выполнил ли TXPG операцию до того, как мы перестали слушать, неизвестно.
            log.error("PROVIDER REQ [{}] <- OUTCOME UNKNOWN for ProviderOrderId: {}. No response from the acquirer: {}",
                    action, providerOrderId, e.getMessage());
            return new PaymentOutcomeUnknownException(
                    "No response from the acquirer for the " + action + ": " + e.getMessage(), e);
        }
        log.error("PROVIDER REQ [{}] <- OUTCOME UNKNOWN for ProviderOrderId: {}. Unexpected failure: {}",
                action, providerOrderId, e.getMessage());
        return new PaymentOutcomeUnknownException(
                "Acquirer call for the " + action + " failed without a verdict: " + e.getMessage(), e);
    }

    private void checkAndThrowIfErrorCode(Map<String, Object> response, String action) {
        if (response != null && response.containsKey("errorCode")) {
            String errorCode = String.valueOf(response.get("errorCode"));
            String errorDesc = response.containsKey("errorDescription") && response.get("errorDescription") != null
                    ? String.valueOf(response.get("errorDescription"))
                    : errorCode;
            log.warn("PROVIDER RESP [{}] <- REJECTED BY MILLIKART. ErrorCode: {}, Description: {}", action, errorCode, errorDesc);
            throw new AcquirerDeclinedException("Acquirer error: " + errorDesc);
        }
    }

    // 4xx — шлюз отказал (AcquirerDeclinedException, breaker его не считает), 5xx — сбой шлюза.
    private BusinessException acquirerError(HttpStatusCodeException e) {
        String message = "Acquirer error: " + extractErrorDescription(e.getResponseBodyAsString());
        return e.getStatusCode().is4xxClientError()
                ? new AcquirerDeclinedException(message)
                : new BusinessException(message);
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
