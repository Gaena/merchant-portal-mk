package az.millikart.pbl;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.PaymentOutcomeUnknownException;
import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.provider.TxpgAcquiringClient;
import az.millikart.pbl.provider.dto.EcomCreateOrderResponse;
import az.millikart.pbl.provider.dto.MoneyOperationResult;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

// P0-7: как реальный клиент эквайринга классифицирует неудавшееся движение денег. Два вывода
// не взаимозаменяемы: "шлюз отказал" — ничего не сдвинулось, повтор безопасен, HTTP 400 — и
// "неизвестно" — могло исполниться, HTTP 502. Раньше всё сводилось к первому, и оборванный refund
// оборачивался ручным повтором. Плюс P0-9: order password едет в query (Р-25) и в теле (§5.8.3).
class TxpgAcquiringClientTest {

    private static final String ORDER_ID = "1234567";
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    // Ответ на refund из §5.7 контракта, дословно.
    private static final String CONFIRMED_BODY = "{\"tran\":{\"approvalCode\":\"340775\","
            + "\"match\":{\"tranActionId\":\"220613-09172925-000hbr=\",\"ridByPmo\":\"220613334596244733\"}}}";

    private TxpgAcquiringClient client;
    private MockRestServiceServer server;
    private ListAppender<ILoggingEvent> logEvents;
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TxpgAcquiringClient(
                builder.build(),
                "https://api.txpg.example.com",
                "https://gateway.txpg.example.com",
                "/order",
                "/order/{orderId}/exec-tran",
                "/order/{orderId}");

        logEvents = new ListAppender<>();
        logEvents.start();
        // Ловим всё, что клиент вообще пишет, включая DEBUG: проверки P0-9 идут по всем уровням, а
        // тела запроса и ответа логируются на DEBUG. Уровень, выставленный другим тестом, не решает.
        previousLevel = clientLogger().getLevel();
        clientLogger().setLevel(Level.TRACE);
        clientLogger().addAppender(logEvents);
    }

    @AfterEach
    void tearDown() {
        clientLogger().detachAppender(logEvents);
        clientLogger().setLevel(previousLevel);
    }

    // --- шлюз вынес вердикт: отказ, ничего не сдвинулось -------------------------------------

    @Test
    void refund_errorCodeInsideSuccessfulResponse_isAPlainRefusal() {
        respondWith(withSuccess("{\"errorCode\":\"116\",\"errorDescription\":\"Not enough funds\"}",
                MediaType.APPLICATION_JSON));

        BusinessException thrown = Assertions.assertThrows(BusinessException.class, this::refund);
        Assertions.assertTrue(thrown.getMessage().contains("Not enough funds"),
                "the gateway's own wording must survive: " + thrown.getMessage());
    }

    @Test
    void refund_clientError_isAPlainRefusal() {
        respondWith(withBadRequest().body("{\"errorDescription\":\"Order already reversed\"}")
                .contentType(MediaType.APPLICATION_JSON));

        Assertions.assertThrows(BusinessException.class, this::refund);
    }

    @Test
    void completeDms_errorCodeInsideSuccessfulResponse_isAPlainRefusal() {
        respondWith(withSuccess("{\"errorCode\":\"116\",\"errorDescription\":\"Order not authorized\"}",
                MediaType.APPLICATION_JSON));

        Assertions.assertThrows(BusinessException.class, this::completeDms);
    }

    @Test
    void completeDms_clientError_isAPlainRefusal() {
        respondWith(withBadRequest().body("{\"errorDescription\":\"Unknown order\"}")
                .contentType(MediaType.APPLICATION_JSON));

        Assertions.assertThrows(BusinessException.class, this::completeDms);
    }

    // --- исход неизвестен: операция могла уже исполниться ------------------------------------

    // Шлюз принял запрос и упал уже за ним — операция могла исполниться.
    @Test
    void refund_serverError_leavesTheOutcomeUnknown() {
        respondWith(withServerError());

        Assertions.assertThrows(PaymentOutcomeUnknownException.class, this::refund);
    }

    // Read timeout ничего не говорит о том, исполнил ли TXPG refund до обрыва.
    @Test
    void refund_readTimeout_leavesTheOutcomeUnknown() {
        respondWith(request -> {
            throw new IOException("Read timed out");
        });

        Assertions.assertThrows(PaymentOutcomeUnknownException.class, this::refund);
    }

    @Test
    void completeDms_serverError_leavesTheOutcomeUnknown() {
        respondWith(withServerError());

        Assertions.assertThrows(PaymentOutcomeUnknownException.class, this::completeDms);
    }

    @Test
    void completeDms_connectionFailure_leavesTheOutcomeUnknown() {
        respondWith(request -> {
            throw new IOException("Connection refused");
        });

        Assertions.assertThrows(PaymentOutcomeUnknownException.class, this::completeDms);
    }

    // --- P0-8: что реально уходит на провод --------------------------------------------------

    // Тело Clearing раньше шло без amount, и эквайер списывал весь холд независимо от того, что
    // просил мерчант. MilliKart подтвердили, что фаза принимает amount.
    @Test
    void completeDms_sendsClearingPhaseWithTheAmount() {
        server.expect(requestTo(Matchers.containsString("/order/" + ORDER_ID + "/exec-tran")))
                .andExpect(MockRestRequestMatchers.content()
                        .json("{\"tran\":{\"phase\":\"Clearing\",\"amount\":\"500.00\"}}", true))
                .andRespond(withSuccess(CONFIRMED_BODY, MediaType.APPLICATION_JSON));

        // На входе scale 0, на выходе два знака: шлюзу уходит денежная строка, не голое целое.
        client.completeDms(ORDER_ID, "order-password", "TerminalSys/Admin", "terminal-password",
                new BigDecimal("500"));

        server.verify();
    }

    // Здесь стоял BigDecimal.toString(), а он даёт научную нотацию: new BigDecimal("1E+3")
    // печатается как "1E+3" — для шлюза это не тысяча манат. Scale приходит из JSON вызывающего.
    @Test
    void refund_sendsThePlainAmountNotScientificNotation() {
        server.expect(requestTo(Matchers.containsString("/order/" + ORDER_ID + "/exec-tran")))
                .andExpect(MockRestRequestMatchers.content()
                        .json("{\"tran\":{\"phase\":\"Single\",\"type\":\"Refund\",\"amount\":\"1000.00\"}}", true))
                .andRespond(withSuccess(CONFIRMED_BODY, MediaType.APPLICATION_JSON));

        client.refund(ORDER_ID, "order-password", "TerminalSys/Admin", "terminal-password",
                new BigDecimal("1E+3"));

        server.verify();
    }

    // --- P1-8b: успех — это подтверждение эквайера, а не отсутствие ошибки -------------------

    // Ответ §5.7 дословно: все три идентификатора на месте, сырое тело остаётся в providerResponse.
    @Test
    void refund_confirmedResponse_yieldsAllThreeIdentifiers() {
        respondWith(withSuccess(CONFIRMED_BODY, MediaType.APPLICATION_JSON));

        MoneyOperationResult result = refund();

        Assertions.assertEquals("340775", result.approvalCode());
        Assertions.assertEquals("220613-09172925-000hbr=", result.tranActionId());
        Assertions.assertEquals("220613334596244733", result.ridByPmo());
        Assertions.assertNotNull(result.raw(), "the raw body must be kept for providerResponse");
        Assertions.assertTrue(result.raw().containsKey("tran"), "the raw body is the response as it came");
    }

    // Главный тест P1-8b: "нет errorCode" читалось как "получилось", тогда как маркер успеха по
    // контракту — tran.match.ridByPmo. Без него не известно ничего: 502, а не 400 (Р-23).
    @Test
    void refund_responseWithoutRidByPmo_leavesTheOutcomeUnknown() {
        respondWith(withSuccess("{\"tran\":{\"approvalCode\":\"340775\","
                + "\"match\":{\"tranActionId\":\"220613-09172925-000hbr=\"}}}", MediaType.APPLICATION_JSON));

        PaymentOutcomeUnknownException thrown =
                Assertions.assertThrows(PaymentOutcomeUnknownException.class, this::refund);
        Assertions.assertTrue(thrown.getMessage().contains("tran.match.ridByPmo"),
                "the message must name the missing field: " + thrown.getMessage());
        Assertions.assertTrue(errorLogs().anyMatch(m -> m.contains("NO CONFIRMATION") && m.contains("tranActionId")),
                "the full body must be in the ERROR log so a shape mismatch is visible at once");
    }

    // Пустой объект, tran нет вовсе: тот же вердикт и так же без ClassCast.
    @Test
    void completeDms_emptyObjectResponse_leavesTheOutcomeUnknown() {
        respondWith(withSuccess("{}", MediaType.APPLICATION_JSON));

        PaymentOutcomeUnknownException thrown =
                Assertions.assertThrows(PaymentOutcomeUnknownException.class, this::completeDms);
        Assertions.assertTrue(thrown.getMessage().contains("tran.match.ridByPmo"), thrown.getMessage());
    }

    // tran есть, а match — не map. Защитное чтение: неверный тип это "не подтверждено", а не
    // ClassCastException из денежного потока.
    @Test
    void refund_matchIsNotAnObject_leavesTheOutcomeUnknownWithoutClassCast() {
        respondWith(withSuccess("{\"tran\":{\"approvalCode\":\"340775\",\"match\":\"220613334596244733\"}}",
                MediaType.APPLICATION_JSON));

        assertUnconfirmedNotWrapped(Assertions.assertThrows(PaymentOutcomeUnknownException.class, this::refund));
    }

    @Test
    void completeDms_matchIsANumber_leavesTheOutcomeUnknownWithoutClassCast() {
        respondWith(withSuccess("{\"tran\":{\"match\":42}}", MediaType.APPLICATION_JSON));

        assertUnconfirmedNotWrapped(Assertions.assertThrows(PaymentOutcomeUnknownException.class, this::completeDms));
    }

    // catch-all в classifyMoneyOperationFailure заворачивает ClassCastException в тот же тип, так
    // что тип сам по себе ничего не доказывает: у честного вердикта есть формулировка про
    // tran.match.ridByPmo и нет cause, у завёрнутого каста — ни того, ни другого.
    private static void assertUnconfirmedNotWrapped(PaymentOutcomeUnknownException thrown) {
        Assertions.assertTrue(thrown.getMessage().contains("tran.match.ridByPmo"),
                "must be the confirmation verdict, not a wrapped failure: " + thrown.getMessage());
        Assertions.assertNull(thrown.getCause(),
                "a wrapped exception (e.g. ClassCastException) would show up as the cause: " + thrown.getCause());
    }

    // Шлюз, сериализующий id числом, всё равно подтверждает; id хранится текстом.
    @Test
    void refund_ridByPmoAsNumber_isAcceptedAsText() {
        respondWith(withSuccess("{\"tran\":{\"approvalCode\":\"340775\","
                + "\"match\":{\"tranActionId\":\"220613-09172925-000hbr=\",\"ridByPmo\":220613334596244733}}}",
                MediaType.APPLICATION_JSON));

        MoneyOperationResult result = refund();

        Assertions.assertEquals("220613334596244733", result.ridByPmo());
    }

    // ridByPmo — это вердикт, approvalCode — для разбора спора. Нет approvalCode: успех, но с WARN.
    @Test
    void refund_ridByPmoWithoutApprovalCode_isConfirmedButWarned() {
        respondWith(withSuccess("{\"tran\":{\"match\":{\"tranActionId\":\"220613-09172925-000hbr=\","
                + "\"ridByPmo\":\"220613334596244733\"}}}", MediaType.APPLICATION_JSON));

        MoneyOperationResult result = refund();

        Assertions.assertEquals("220613334596244733", result.ridByPmo());
        Assertions.assertNull(result.approvalCode());
        Assertions.assertTrue(warnLogs().anyMatch(m -> m.contains("approvalCode")),
                "a missing approvalCode must be visible in the WARN log");
    }

    // То же для tranActionId: предупреждаем, но не отказываем.
    @Test
    void completeDms_ridByPmoWithoutTranActionId_isConfirmedButWarned() {
        respondWith(withSuccess("{\"tran\":{\"approvalCode\":\"340775\",\"match\":{\"ridByPmo\":\"220613334596244733\"}}}",
                MediaType.APPLICATION_JSON));

        MoneyOperationResult result = completeDms();

        Assertions.assertEquals("220613334596244733", result.ridByPmo());
        Assertions.assertNull(result.tranActionId());
        Assertions.assertTrue(warnLogs().anyMatch(m -> m.contains("tranActionId")));
    }

    // Пустой id никого не идентифицирует — он всё равно что отсутствует.
    @Test
    void refund_blankRidByPmo_leavesTheOutcomeUnknown() {
        respondWith(withSuccess("{\"tran\":{\"match\":{\"ridByPmo\":\"  \"}}}", MediaType.APPLICATION_JSON));

        Assertions.assertThrows(PaymentOutcomeUnknownException.class, this::refund);
    }

    // Идентификатор — скаляр, строка или число. Структура на его месте значит, что ответ вообще не
    // по §5.5–5.7, и String.valueOf от неё ("{}", "[]", "{id=x}") не должен сойти за подтверждение.
    @Test
    void refund_ridByPmoAsObject_leavesTheOutcomeUnknown() {
        respondWith(withSuccess("{\"tran\":{\"match\":{\"ridByPmo\":{}}}}", MediaType.APPLICATION_JSON));

        assertUnconfirmedNotWrapped(Assertions.assertThrows(PaymentOutcomeUnknownException.class, this::refund));
    }

    @Test
    void refund_ridByPmoAsList_leavesTheOutcomeUnknown() {
        respondWith(withSuccess("{\"tran\":{\"match\":{\"ridByPmo\":[]}}}", MediaType.APPLICATION_JSON));

        assertUnconfirmedNotWrapped(Assertions.assertThrows(PaymentOutcomeUnknownException.class, this::refund));
    }

    @Test
    void refund_ridByPmoAsNestedObject_leavesTheOutcomeUnknown() {
        respondWith(withSuccess("{\"tran\":{\"match\":{\"ridByPmo\":{\"id\":\"x\"}}}}", MediaType.APPLICATION_JSON));

        PaymentOutcomeUnknownException thrown =
                Assertions.assertThrows(PaymentOutcomeUnknownException.class, this::refund);
        assertUnconfirmedNotWrapped(thrown);
        Assertions.assertFalse(thrown.getMessage().contains("{id=x}"),
                "a java rendering of the structure must not leak into the verdict: " + thrown.getMessage());
    }

    // То же правило для необязательных идентификаторов, но с необязательным следствием: структура
    // в approvalCode — это "нет значения" (WARN), а подтверждённая операция держится на ridByPmo.
    @Test
    void refund_approvalCodeAsObject_isTreatedAsAbsent() {
        respondWith(withSuccess("{\"tran\":{\"approvalCode\":{\"code\":\"340775\"},"
                + "\"match\":{\"tranActionId\":\"220613-09172925-000hbr=\",\"ridByPmo\":\"220613334596244733\"}}}",
                MediaType.APPLICATION_JSON));

        MoneyOperationResult result = refund();

        Assertions.assertEquals("220613334596244733", result.ridByPmo());
        Assertions.assertNull(result.approvalCode(), "a structure is not an approval code");
        Assertions.assertTrue(warnLogs().anyMatch(m -> m.contains("approvalCode")),
                "the missing approvalCode must be visible in the WARN log");
    }

    // Порядок проверок не менялся: errorCode — по-прежнему определённый отказ (400) и решается до
    // проверки подтверждения, так что отказ не будет прочитан как 502.
    @Test
    void refund_errorCodeStillWinsOverMissingConfirmation() {
        respondWith(withSuccess("{\"errorCode\":\"116\",\"errorDescription\":\"Not enough funds\"}",
                MediaType.APPLICATION_JSON));

        BusinessException thrown = Assertions.assertThrows(BusinessException.class, this::refund);
        Assertions.assertTrue(thrown.getMessage().contains("Not enough funds"));
    }

    // --- P0-9: order password отправляется, но не логируется -------------------------------

    private static final String ORDER_PASSWORD = "1h1pq153fk8xk";

    // Тело статуса из §5.8.3: объект order несёт password на верхнем уровне.
    private static final String ORDER_STATUS_BODY = "{\"order\":{\"id\":" + ORDER_ID + ","
            + "\"hppUrl\":\"https://test.millikart.az:8004\",\"password\":\"" + ORDER_PASSWORD + "\","
            + "\"status\":\"FullyPaid\",\"ridByMerchant\":\"123123871283618376123\",\"amount\":5,"
            + "\"currency\":\"AZN\"}}";

    // Ответ create-order из §5.3: свежевыданный password возвращается в теле.
    private static final String CREATED_ORDER_BODY = "{\"order\":{\"hppUrl\":\"https://test.millikart.az:8004\","
            + "\"id\":11338,\"status\":\"Preparing\",\"password\":\"" + ORDER_PASSWORD + "\"}}";

    // Опрос статуса течёт дважды: password в URL запроса (Р-25) и снова в ответе (§5.8.3). URL
    // логируется без query, тело — без ключа; тело всё же логируется, остальное в нём и есть диагноз.
    @Test
    void getOrderStatus_neverLogsTheOrderPassword() {
        server.expect(requestTo(Matchers.containsString("/order/" + ORDER_ID + "?")))
                .andExpect(requestTo(Matchers.containsString("password=" + ORDER_PASSWORD)))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andRespond(withSuccess(ORDER_STATUS_BODY, MediaType.APPLICATION_JSON));

        Map<String, Object> order = client.getOrderStatus(ORDER_ID, ORDER_PASSWORD, "TerminalSys/Admin", "terminal-password");

        server.verify();
        Assertions.assertEquals("FullyPaid", order.get("status"), "the order object is what comes back");
        assertNoLogLineContains(ORDER_PASSWORD);
        Assertions.assertTrue(allLogs().anyMatch(m -> m.contains("/order/" + ORDER_ID) && !m.contains("?")),
                "the address must still be logged, without its query string: " + allLogs().toList());
        Assertions.assertTrue(allLogs().anyMatch(m -> m.contains("FullyPaid") && m.contains("ridByMerchant")),
                "the payload must still be logged, minus the password: " + allLogs().toList());
    }

    // Отказной опрос: ветка errorCode тоже логирует тело, строка запроса та же.
    @Test
    void getOrderStatus_errorCode_neverLogsTheOrderPassword() {
        server.expect(requestTo(Matchers.containsString("password=" + ORDER_PASSWORD)))
                .andRespond(withSuccess("{\"errorCode\":\"116\",\"errorDescription\":\"Unknown order\"}",
                        MediaType.APPLICATION_JSON));

        Assertions.assertThrows(BusinessException.class,
                () -> client.getOrderStatus(ORDER_ID, ORDER_PASSWORD, "TerminalSys/Admin", "terminal-password"));

        assertNoLogLineContains(ORDER_PASSWORD);
    }

    // URL с паролем попадает и в сообщения исключений Spring — проверяем и это.
    @Test
    void getOrderStatus_connectionFailure_neverLogsTheOrderPassword() {
        server.expect(requestTo(Matchers.containsString("password=" + ORDER_PASSWORD)))
                .andRespond(request -> {
                    throw new IOException("Read timed out");
                });

        Assertions.assertThrows(BusinessException.class,
                () -> client.getOrderStatus(ORDER_ID, ORDER_PASSWORD, "TerminalSys/Admin", "terminal-password"));

        assertNoLogLineContains(ORDER_PASSWORD);
    }

    // Р-25 на проводе, P0-9 в логе: клиринг шлёт password и не логирует его.
    @Test
    void completeDms_neverLogsTheOrderPassword() {
        server.expect(requestTo(Matchers.containsString("/order/" + ORDER_ID + "/exec-tran?password=" + ORDER_PASSWORD)))
                .andRespond(withSuccess(CONFIRMED_BODY, MediaType.APPLICATION_JSON));

        client.completeDms(ORDER_ID, ORDER_PASSWORD, "TerminalSys/Admin", "terminal-password", AMOUNT);

        server.verify();
        assertNoLogLineContains(ORDER_PASSWORD);
        Assertions.assertTrue(allLogs().anyMatch(m -> m.contains("/order/" + ORDER_ID + "/exec-tran")),
                "the address must still be logged: " + allLogs().toList());
    }

    @Test
    void refund_neverLogsTheOrderPassword() {
        server.expect(requestTo(Matchers.containsString("/order/" + ORDER_ID + "/exec-tran?password=" + ORDER_PASSWORD)))
                .andRespond(withSuccess(CONFIRMED_BODY, MediaType.APPLICATION_JSON));

        client.refund(ORDER_ID, ORDER_PASSWORD, "TerminalSys/Admin", "terminal-password", AMOUNT);

        server.verify();
        assertNoLogLineContains(ORDER_PASSWORD);
    }

    // Ветки отказа логируют исключение, а сообщение оборванного вызова называет URL. Spring режет
    // там query; здесь это закреплено для денежных вызовов, чьи ERROR-строки чаще всего идут в алерт.
    @Test
    void refund_readTimeout_neverLogsTheOrderPassword() {
        server.expect(requestTo(Matchers.containsString("password=" + ORDER_PASSWORD)))
                .andRespond(request -> {
                    throw new IOException("Read timed out");
                });

        Assertions.assertThrows(PaymentOutcomeUnknownException.class,
                () -> client.refund(ORDER_ID, ORDER_PASSWORD, "TerminalSys/Admin", "terminal-password", AMOUNT));

        assertNoLogLineContains(ORDER_PASSWORD);
    }

    @Test
    void completeDms_serverError_neverLogsTheOrderPassword() {
        server.expect(requestTo(Matchers.containsString("password=" + ORDER_PASSWORD)))
                .andRespond(withServerError().body("{\"message\":\"gateway down\"}"));

        Assertions.assertThrows(PaymentOutcomeUnknownException.class,
                () -> client.completeDms(ORDER_ID, ORDER_PASSWORD, "TerminalSys/Admin", "terminal-password", AMOUNT));

        assertNoLogLineContains(ORDER_PASSWORD);
    }

    // Строка ERROR "Full body" из P1-8b — это лог сырого тела эквайера. Контракт не кладёт password
    // в ответ exec-tran, но если он там окажется, утечь через эту строку он не должен. Остальное тело
    // логируется: строка и существует, чтобы показать расхождение формы.
    @Test
    void refund_unconfirmedResponse_fullBodyLogIsWithoutSecrets() {
        server.expect(requestTo(Matchers.containsString("password=" + ORDER_PASSWORD)))
                .andRespond(withSuccess("{\"tran\":{\"approvalCode\":\"340775\"},\"password\":\"" + ORDER_PASSWORD + "\"}",
                        MediaType.APPLICATION_JSON));

        Assertions.assertThrows(PaymentOutcomeUnknownException.class,
                () -> client.refund(ORDER_ID, ORDER_PASSWORD, "TerminalSys/Admin", "terminal-password", AMOUNT));

        assertNoLogLineContains(ORDER_PASSWORD);
        Assertions.assertTrue(errorLogs().anyMatch(m -> m.contains("NO CONFIRMATION") && m.contains("340775")),
                "the rest of the body must still be in the ERROR log");
    }

    // При создании заказа password и рождается: он приходит в теле, которое логируется на DEBUG.
    // toString() рекорда его маскирует, поэтому строка безопасна на любом уровне — а сам password,
    // конечно, возвращается: из него строится редирект плательщика.
    @Test
    void createEcomOrder_neverLogsTheOrderPassword() {
        server.expect(requestTo("https://gateway.txpg.example.com/order"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andRespond(withSuccess(CREATED_ORDER_BODY, MediaType.APPLICATION_JSON));
        PaymentLink link = PaymentLink.builder()
                .paymentType(PaymentType.SMS)
                .amount(new BigDecimal("5.00"))
                .currency("AZN")
                .description("P0-9 fixture")
                .build();

        EcomCreateOrderResponse response = client.createEcomOrder(link, "TerminalSys/Admin", "terminal-password",
                UUID.randomUUID(), "https://pay.example.com/api/v1/payment-links/redirect/x");

        server.verify();
        Assertions.assertEquals(ORDER_PASSWORD, response.order().password(), "the password is returned, only not logged");
        Assertions.assertTrue(allLogs().anyMatch(m -> m.contains("PROVIDER RESP BODY [createEcomOrder]")),
                "the DEBUG body line must have been captured, or this test proves nothing: " + allLogs().toList());
        assertNoLogLineContains(ORDER_PASSWORD);
    }

    // Гарантия, на которой держится предыдущий случай: рекорд печатает все компоненты, поэтому
    // единственный способ сделать log.debug("{}", response) безопасным — toString() с маской.
    @Test
    void ecomCreateOrderResponse_toString_masksThePasswordAndKeepsTheRest() {
        EcomCreateOrderResponse.Order order = new EcomCreateOrderResponse.Order(
                "https://test.millikart.az:8004", 11338L, "Preparing", ORDER_PASSWORD);
        EcomCreateOrderResponse response = new EcomCreateOrderResponse(order);

        for (String rendered : List.of(order.toString(), response.toString())) {
            Assertions.assertFalse(rendered.contains(ORDER_PASSWORD), rendered);
            Assertions.assertTrue(rendered.contains("password=***"), rendered);
            Assertions.assertTrue(rendered.contains("https://test.millikart.az:8004"), rendered);
            Assertions.assertTrue(rendered.contains("11338"), rendered);
            Assertions.assertTrue(rendered.contains("Preparing"), rendered);
        }
        Assertions.assertEquals(ORDER_PASSWORD, order.password(), "masking is for toString() only");
    }

    // Отсутствующий password — заметная проблема; маска его не прячет.
    @Test
    void ecomCreateOrderResponse_toString_showsAMissingPasswordAsNull() {
        String rendered = new EcomCreateOrderResponse.Order("https://test.millikart.az:8004", 11338L, "Preparing", null).toString();

        Assertions.assertTrue(rendered.contains("password=null"), rendered);
    }

    private void assertNoLogLineContains(String secret) {
        List<String> offending = allLogs().filter(m -> m.contains(secret)).toList();
        Assertions.assertTrue(offending.isEmpty(), "the order password reached the log: " + offending);
        Assertions.assertFalse(allLogs().toList().isEmpty(), "nothing was logged at all, so nothing was checked");
    }

    // --- вспомогательное ---------------------------------------------------------------------

    private void respondWith(ResponseCreator responseCreator) {
        server.expect(requestTo(Matchers.containsString("/order/" + ORDER_ID + "/exec-tran")))
                .andRespond(responseCreator);
    }

    private MoneyOperationResult refund() {
        return client.refund(ORDER_ID, "order-password", "TerminalSys/Admin", "terminal-password", AMOUNT);
    }

    private MoneyOperationResult completeDms() {
        return client.completeDms(ORDER_ID, "order-password", "TerminalSys/Admin", "terminal-password", AMOUNT);
    }

    private static Logger clientLogger() {
        return (Logger) LoggerFactory.getLogger(TxpgAcquiringClient.class);
    }

    private Stream<String> warnLogs() {
        return logsAt(Level.WARN);
    }

    private Stream<String> errorLogs() {
        return logsAt(Level.ERROR);
    }

    private Stream<String> logsAt(Level level) {
        return logEvents.list.stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage);
    }

    // Каждая пойманная строка любого уровня, вместе с отрисованным throwable: сообщение исключения
    // с URL запроса иначе проскочило бы мимо проверки только по тексту сообщения.
    private Stream<String> allLogs() {
        return logEvents.list.stream().map(event -> {
            StringBuilder line = new StringBuilder(event.getFormattedMessage());
            for (var proxy = event.getThrowableProxy(); proxy != null; proxy = proxy.getCause()) {
                line.append(" | ").append(proxy.getClassName()).append(": ").append(proxy.getMessage());
            }
            return line.toString();
        });
    }
}
