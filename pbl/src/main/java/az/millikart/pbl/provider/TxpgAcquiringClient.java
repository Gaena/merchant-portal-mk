package az.millikart.pbl.provider;

import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.PaymentOutcomeUnknownException;
import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.provider.dto.EcomCreateOrderRequest;
import az.millikart.pbl.provider.dto.EcomCreateOrderResponse;
import az.millikart.pbl.provider.dto.MoneyOperationResult;
import az.millikart.pbl.provider.dto.TerminalCheckResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Единственный AcquiringClient. Стаб под флагом pbl.provider.stub убран (20.08.2026): флаг мог
// выбрать не тот клиент или ни одного; локальный прогон идёт на стенд MilliKart через
// PBL_PROVIDER_*. Второй реализации в main быть не должно — тестовый двойник живёт в тестах.
@Component
public class TxpgAcquiringClient implements AcquiringClient {

    private static final Logger log = LoggerFactory.getLogger(TxpgAcquiringClient.class);

    private final RestClient restClient;
    private final String apiBaseUrl;
    private final String gatewayBaseUrl;
    private final String createOrderPath;
    private final String execTranPath;
    private final String getOrderPath;

    // Дефолтов здесь нет намеренно: единственное место для них — application.yaml. У адресов их нет
    // и там (P1-10) — сервис, не нашедший адрес, не должен стартовать; пути дефолтятся в yaml.
    // Иначе выпавший из конфигурации ключ молча увёл бы клиента на другой хост или другой путь.
    public TxpgAcquiringClient(
            RestClient restClient,
            @Value("${pbl.provider.api-base-url}") String apiBaseUrl,
            @Value("${pbl.provider.gateway-base-url}") String gatewayBaseUrl,
            @Value("${pbl.provider.create-order-path}") String createOrderPath,
            @Value("${pbl.provider.exec-tran-path}") String execTranPath,
            @Value("${pbl.provider.get-order-path}") String getOrderPath) {
        this.restClient = restClient;
        this.apiBaseUrl = apiBaseUrl;
        this.gatewayBaseUrl = gatewayBaseUrl;
        this.createOrderPath = createOrderPath;
        this.execTranPath = execTranPath;
        this.getOrderPath = getOrderPath;
    }

    // Ретраится намеренно, в отличие от денежных операций ниже: дубль заказа с нашим ridByMerchant
    // остаётся неоплаченным и ничего не стоит — в отличие от дубля возврата или списания холда.
    @Override
    @CircuitBreaker(name = "acquiring")
    @Retry(name = "acquiring")
    public EcomCreateOrderResponse createEcomOrder(PaymentLink link, String login, String password, UUID ridByMerchant, String hppRedirectUrl) {
        String url = UriComponentsBuilder.fromUriString(gatewayBaseUrl)
                .path(createOrderPath)
                .toUriString();

        String typeRid = (link.getPaymentType() == PaymentType.DMS) ? "Order_DMS" : "Order_SMS";

        EcomCreateOrderRequest.SubMerchant subMerchant = new EcomCreateOrderRequest.SubMerchant("https://millikart.az/");

        EcomCreateOrderRequest request = new EcomCreateOrderRequest(
                new EcomCreateOrderRequest.Order(
                        typeRid,
                        ridByMerchant.toString(),
                        link.getAmount(),
                        link.getCurrency(),
                        link.getDescription() != null ? link.getDescription() : "Payment via Pay-By-Link",
                        "az",
                        hppRedirectUrl,
                        subMerchant
                )
        );

        log.info("PROVIDER REQ [createEcomOrder] -> POST URL: {}, Login: {}, RidByMerchant: {}, Type: {}, Amount: {} {}",
                ProviderPayloads.urlForLog(url), login, ridByMerchant, typeRid, link.getAmount(), link.getCurrency());
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

            log.info("PROVIDER RESP [createEcomOrder] <- SUCCESS for RidByMerchant: {}, ProviderOrderId: {}",
                    ridByMerchant, response != null && response.order() != null ? response.order().id() : "N/A");
            // P0-9: в теле — пароль заказа; его маскирует EcomCreateOrderResponse.Order.toString().
            log.debug("PROVIDER RESP BODY [createEcomOrder]: {}", response);
            return response;
        } catch (HttpStatusCodeException e) {
            log.error("PROVIDER RESP [createEcomOrder] <- FAILED. HTTP Status: {}, Error Body: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            String desc = extractErrorDescription(e.getResponseBodyAsString());
            throw new BusinessException("Acquirer error: " + desc);
        } catch (Exception e) {
            log.error("PROVIDER REQ [createEcomOrder] <- CONNECTION EXCEPTION: {}", e.getMessage(), e);
            throw new BusinessException("Acquirer connection failed: " + e.getMessage());
        }
    }

    // P0-7: намеренно без @Retry. Списание холда не идемпотентно — на таймауте чтения capture мог
    // уже пройти, и повтор спишет с держателя карты дважды. Circuit breaker остаётся: он только
    // отказывает в новых вызовах, но никогда не пересылает уже отправленный.
    @Override
    @CircuitBreaker(name = "acquiring")
    @SuppressWarnings("unchecked")
    public MoneyOperationResult completeDms(String providerOrderId, String password, String login, String terminalPassword, BigDecimal amount) {
        // Р-25: пароль заказа уходит в query-строке, поэтому URL нельзя логировать иначе как через
        // ProviderPayloads.urlForLog (P0-9). По контракту пароль в адресе нужен только для
        // GET /order/{id}; для exec-tran его добавили мы, но убирать нельзя без прогона на стенде —
        // это денежный путь (AGENTS.md §10).
        String url = UriComponentsBuilder.fromUriString(apiBaseUrl)
                .path(execTranPath)
                .queryParam("password", password)
                .buildAndExpand(providerOrderId)
                .toUriString();

        Map<String, Object> tran = new HashMap<>();
        tran.put("phase", "Clearing");
        // P0-8: раньше amount здесь терялся, эквайер списывал весь холд, а API изображал удавшийся
        // частичный capture. MilliKart подтвердили, что phase "Clearing" принимает amount.
        tran.put("amount", formatAmount(amount));
        Map<String, Object> body = new HashMap<>();
        body.put("tran", tran);

        log.info("PROVIDER REQ [completeDms] -> POST URL: {}, ProviderOrderId: {}, Login: {}, Amount: {}",
                ProviderPayloads.urlForLog(url), providerOrderId, login, amount);
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

            log.info("PROVIDER RESP [completeDms] <- SUCCESS for ProviderOrderId: {}, Response: {}",
                    providerOrderId, ProviderPayloads.withoutSecrets(response));
            checkAndThrowIfErrorCode(response, "completeDms");
            return requireConfirmation("completeDms", providerOrderId, response);
        } catch (Exception e) {
            throw classifyMoneyOperationFailure("completeDms", providerOrderId, e);
        }
    }

    // P0-7: намеренно без @Retry, по той же причине, что completeDms. Повторённый возврат — худший
    // случай: мерчант записывает один возврат, а эквайер выплачивает до трёх.
    @Override
    @CircuitBreaker(name = "acquiring")
    @SuppressWarnings("unchecked")
    public MoneyOperationResult refund(String providerOrderId, String password, String login, String terminalPassword, BigDecimal amount) {
        String url = UriComponentsBuilder.fromUriString(apiBaseUrl)
                .path(execTranPath)
                .queryParam("password", password)
                .buildAndExpand(providerOrderId)
                .toUriString();

        Map<String, Object> tran = new HashMap<>();
        tran.put("phase", "Single");
        tran.put("amount", formatAmount(amount));
        tran.put("type", "Refund");
        Map<String, Object> body = new HashMap<>();
        body.put("tran", tran);

        log.info("PROVIDER REQ [refund] -> POST URL: {}, ProviderOrderId: {}, Login: {}, Refund Amount: {}",
                ProviderPayloads.urlForLog(url), providerOrderId, login, amount);
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

            log.info("PROVIDER RESP [refund] <- SUCCESS for ProviderOrderId: {}, Response: {}",
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
    public Map<String, Object> getOrderStatus(String providerOrderId, String password, String login, String terminalPassword) {
        String url = UriComponentsBuilder.fromUriString(apiBaseUrl)
                .path(getOrderPath)
                .queryParam("password", password)
                .queryParam("orderDetailLevel", 2)
                .queryParam("tokenDetailLevel", 2)
                .queryParam("tranDetailLevel", 2)
                .buildAndExpand(providerOrderId)
                .toUriString();

        log.info("PROVIDER REQ [getOrderStatus] -> GET URL: {}, ProviderOrderId: {}, Login: {}",
                ProviderPayloads.urlForLog(url), providerOrderId, login);

        try {
            Map<String, Object> body = restClient.get()
                    .uri(url)
                    .headers(headers -> headers.setBasicAuth(login, terminalPassword))
                    .retrieve()
                    .body(Map.class);

            checkAndThrowIfErrorCode(body, "getOrderStatus");
            Map<String, Object> order = body != null && body.containsKey("order")
                    ? (Map<String, Object>) body.get("order")
                    : body;
            // P0-9: при orderDetailLevel=2 объект order несёт пароль заказа (§5.8.3) — в лог он
            // идёт без этого ключа. Логируется после проверки errorCode, чтобы отказ не
            // объявлялся сначала как SUCCESS.
            log.info("PROVIDER RESP [getOrderStatus] <- SUCCESS for ProviderOrderId: {}, Response: {}",
                    providerOrderId, ProviderPayloads.withoutSecrets(order));
            return order;
        } catch (BusinessException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            log.error("PROVIDER RESP [getOrderStatus] <- FAILED. HTTP Status: {}, Error Body: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            String desc = extractErrorDescription(e.getResponseBodyAsString());
            throw new BusinessException("Acquirer error: " + desc);
        } catch (Exception e) {
            log.error("PROVIDER REQ [getOrderStatus] <- CONNECTION EXCEPTION: {}", e.getMessage(), e);
            throw new BusinessException("Order status check failed: " + e.getMessage());
        }
    }

    // Код ошибки, которым провайдер отвечает на неверный логин или пароль терминала.
    private static final String INVALID_LOGIN = "InvalidLogin";

    // Сумма пробного заказа. Не списывается никогда: заказ остаётся неоплаченным и уходит в
    // Expired. Одна манатка, а не копейка — чтобы проверка не упёрлась в минимальную сумму.
    private static final BigDecimal CHECK_AMOUNT = new BigDecimal("1.00");

    /**
     * Проверка учётных данных терминала пробным заказом.
     *
     * Намеренно **без** `@Retry` и **без** `@CircuitBreaker`, в отличие от боевого заведения
     * заказа. Повтор здесь только множит пробные заказы у провайдера, а общий с платёжным путём
     * breaker означал бы, что администратор, десять раз проверивший неверный пароль, закрывает
     * приём платежей всем мерчантам.
     *
     * Классификация — по коду ошибки, а не по HTTP-статусу: тот же `InvalidLogin` провайдер
     * может прислать и в 200, и в 4xx, и разбирать надо тело в обоих случаях.
     */
    @Override
    public TerminalCheckResult checkTerminalCredentials(String login, String password) {
        String url = UriComponentsBuilder.fromUriString(gatewayBaseUrl)
                .path(createOrderPath)
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
                        new EcomCreateOrderRequest.SubMerchant("https://millikart.az/")
                )
        );

        log.info("PROVIDER REQ [checkTerminalCredentials] -> POST URL: {}, Login: {}",
                ProviderPayloads.urlForLog(url), login);

        try {
            Map<String, Object> body = restClient.post()
                    .uri(url)
                    .headers(headers -> {
                        headers.setBasicAuth(login, password);
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            return classifyCheck(login, body);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is5xxServerError()) {
                log.warn("PROVIDER RESP [checkTerminalCredentials] <- HTTP {} for Login: {}", e.getStatusCode(), login);
                return TerminalCheckResult.unreachable("Acquirer answered HTTP " + e.getStatusCode().value());
            }
            return classifyCheck(login, parseBody(e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.warn("PROVIDER REQ [checkTerminalCredentials] <- no answer for Login: {}: {}", login, e.getMessage());
            return TerminalCheckResult.unreachable("No answer from the acquirer: " + e.getMessage());
        }
    }

    private TerminalCheckResult classifyCheck(String login, Map<String, Object> body) {
        if (body == null) {
            return TerminalCheckResult.unreachable("Empty answer from the acquirer");
        }
        Object errorCode = body.get("errorCode");
        if (errorCode == null) {
            // Заказ заведён: и логин с паролем верны, и оплаты терминалу разрешены.
            log.info("PROVIDER RESP [checkTerminalCredentials] <- OK for Login: {}", login);
            return TerminalCheckResult.ok();
        }
        String code = String.valueOf(errorCode);
        String description = body.get("errorDescription") != null ? String.valueOf(body.get("errorDescription")) : code;
        if (INVALID_LOGIN.equals(code)) {
            log.info("PROVIDER RESP [checkTerminalCredentials] <- invalid credentials for Login: {}", login);
            return new TerminalCheckResult(TerminalCheckResult.Outcome.INVALID_CREDENTIALS, code, description);
        }
        log.info("PROVIDER RESP [checkTerminalCredentials] <- rejected for Login: {} with {}: {}", login, code, description);
        return new TerminalCheckResult(TerminalCheckResult.Outcome.REJECTED, code, description);
    }

    @SuppressWarnings("unchecked")
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

    // Сумма уходит строкой — так в примере Refund у эквайера. toPlainString, а не toString: у
    // BigDecimal из JSON бывает такой scale, что toString даёт "1E+3", и шлюз прочтёт что угодно,
    // кроме 1000.00. RoundingMode.UNNECESSARY — намеренно: за спиной мерчанта ничего не должно
    // округлиться; суммы с тремя знаками отсекает PaymentLinkService — это сломанный инвариант.
    private static String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    // P1-8b: «нет errorCode» — ещё не «прошло». Подтверждение по контракту — tran.match.ridByPmo
    // (§5.5-5.7), его же читает проверка успеха у эквайера (§5.8.8). Без него исход не отказ,
    // а неизвестность (Р-23): PaymentOutcomeUnknownException (502), а не BusinessException (400).
    // Разбор защитный намеренно: кривая форма даёт «не подтверждено», а не ClassCastException.
    private MoneyOperationResult requireConfirmation(String action, String providerOrderId, Map<String, Object> response) {
        Map<String, Object> tran = asMap(response != null ? response.get("tran") : null);
        Map<String, Object> match = asMap(tran != null ? tran.get("match") : null);
        // Что считается идентификатором, решает одно правило на пакет: ProviderPayloads.scalarText.
        String ridByPmo = ProviderPayloads.scalarText(match != null ? match.get("ridByPmo") : null);

        if (ridByPmo == null) {
            // Тело целиком в лог намеренно (Р-23): если реальный шлюз ответит не по §5.5-5.7,
            // возвраты встанут, и одной строки должно хватить, чтобы увидеть, чем он отличается.
            log.error("PROVIDER RESP [{}] <- NO CONFIRMATION for ProviderOrderId: {}. "
                            + "The response carries no tran.match.ridByPmo. Full body: {}",
                    action, providerOrderId, ProviderPayloads.withoutSecrets(response));
            throw new PaymentOutcomeUnknownException(
                    "Acquirer accepted the " + action + " but did not confirm it: the response has no "
                            + "tran.match.ridByPmo, so the operation may or may not have executed. "
                            + "Expected shape — see project_docs/TXPG-client-side-integration.md §5.5-5.7.");
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

    // Делит провал денежной операции на «шлюз отказал» (ничего не двинулось, повтор безопасен) и
    // «мы не знаем» (могло уже пройти). Отказ — только errorCode внутри 200 и 4xx; всё прочее,
    // включая 5xx и таймаут чтения, — неизвестность. По умолчанию «неизвестно» намеренно: раньше
    // всё схлопывалось в BusinessException, мерчант принимал неизвестность за отказ и повторял.
    private RuntimeException classifyMoneyOperationFailure(String action, String providerOrderId, Exception e) {
        if (e instanceof BusinessException businessException) {
            // Из checkAndThrowIfErrorCode: 200 с errorCode. Шлюз прочитал запрос и отказал —
            // значит, точно не выполнил.
            return businessException;
        }
        if (e instanceof PaymentOutcomeUnknownException unconfirmed) {
            // Из requireConfirmation: 200 без tran.match.ridByPmo. Уже нужный тип с нужным
            // текстом — обёртка похоронила бы оба под «failed without a verdict».
            return unconfirmed;
        }
        if (e instanceof HttpStatusCodeException httpError) {
            String desc = extractErrorDescription(httpError.getResponseBodyAsString());
            if (httpError.getStatusCode().is4xxClientError()) {
                log.error("PROVIDER RESP [{}] <- REJECTED for ProviderOrderId: {}. HTTP Status: {}, Error Body: {}",
                        action, providerOrderId, httpError.getStatusCode(), httpError.getResponseBodyAsString(), httpError);
                return new BusinessException("Acquirer error: " + desc);
            }
            // 5xx: шлюз принял запрос и упал уже где-то за ним.
            log.error("PROVIDER RESP [{}] <- OUTCOME UNKNOWN for ProviderOrderId: {}. HTTP Status: {}, Error Body: {}",
                    action, providerOrderId, httpError.getStatusCode(), httpError.getResponseBodyAsString(), httpError);
            return new PaymentOutcomeUnknownException(
                    "Acquirer did not confirm the " + action + " (HTTP " + httpError.getStatusCode() + "): " + desc, httpError);
        }
        if (e instanceof ResourceAccessException) {
            // Таймаут чтения или обрыв: истёкшие 10s ничего не говорят о том, выполнил ли TXPG
            // операцию до того, как мы перестали слушать.
            log.error("PROVIDER REQ [{}] <- OUTCOME UNKNOWN for ProviderOrderId: {}. No response from the acquirer: {}",
                    action, providerOrderId, e.getMessage(), e);
            return new PaymentOutcomeUnknownException(
                    "No response from the acquirer for the " + action + ": " + e.getMessage(), e);
        }
        log.error("PROVIDER REQ [{}] <- OUTCOME UNKNOWN for ProviderOrderId: {}. Unexpected failure: {}",
                action, providerOrderId, e.getMessage(), e);
        return new PaymentOutcomeUnknownException(
                "Acquirer call for the " + action + " failed without a verdict: " + e.getMessage(), e);
    }

    private void checkAndThrowIfErrorCode(Map<String, Object> response, String action) {
        if (response != null && response.containsKey("errorCode")) {
            String errorCode = String.valueOf(response.get("errorCode"));
            String errorDesc = response.containsKey("errorDescription") && response.get("errorDescription") != null
                    ? String.valueOf(response.get("errorDescription"))
                    : errorCode;
            log.error("PROVIDER RESP [{}] <- REJECTED BY MILLIKART. ErrorCode: {}, Description: {}", action, errorCode, errorDesc);
            throw new BusinessException("Acquirer error: " + errorDesc);
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
