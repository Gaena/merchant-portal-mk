package az.millikart.pbl;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.common.security.JwtProvider;
import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.domain.Transaction;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.pbl.domain.UsageType;
import az.millikart.pbl.provider.AcquiringClient;
import az.millikart.pbl.provider.dto.EcomCreateOrderResponse;
import az.millikart.pbl.provider.dto.MoneyOperationResult;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// P2-16, Р-49: возврат не отменяет использование ссылки. refund() переписывает статус самой
// строки платежа (REFUNDED или PARTIALLY_REFUNDED, второй строки нет), поэтому подсчёт только по
// SUCCESS стирал возвращённые платежи из арифметики использований: слот освобождался, и ссылка
// на 3 платежа собирала за жизнь 4.
@SpringBootTest
@AutoConfigureMockMvc
class PaymentLinkRefundUsageTest {

    private static final int TERMINAL_ID = 123456789;
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private PaymentLinkRepository paymentLinkRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TerminalRepository terminalRepository;

    // Провайдер мокается, а не берётся из stub-конфига: возвраты требуют своего ответа эквайера
    // в каждом тесте.
    @MockBean
    private AcquiringClient acquiringClient;

    private final AtomicLong providerOrderIds = new AtomicLong(9_000_000);

    // COMPANY_HEAD компании терминала: может читать, PATCH и возвращать.
    private String headToken;

    @BeforeEach
    void setup() {
        transactionRepository.deleteAll();
        paymentLinkRepository.deleteAll();
        terminalRepository.deleteAll();

        terminalRepository.save(Terminal.builder()
                .id(TERMINAL_ID)
                .name("Test Terminal")
                .login("TerminalSys/Admin")
                .password("1234")
                .companyId("test-company")
                .build());

        headToken = "Bearer " + jwtProvider.generateToken(
                "head-user", "head-user@test.com", "COMPANY_HEAD", "test-company");

        when(acquiringClient.createEcomOrder(any(), anyString(), anyString(), any(), anyString()))
                .thenAnswer(invocation -> {
                    long orderId = providerOrderIds.incrementAndGet();
                    return new EcomCreateOrderResponse(new EcomCreateOrderResponse.Order(
                            "https://gateway.txpg.example.com/pay", orderId, "Preparing", "pwd-" + orderId));
                });
    }

    // 1. Счётчик: возвращённый платёж остаётся учтённым

    @Test
    void twoPaymentsOneRefunded_currentPaymentsCountStaysTwo() throws Exception {
        PaymentLink link = seedLink(UsageType.MULTIPLE, 3);
        seedTransaction(link, TransactionStatus.SUCCESS);
        seedTransaction(link, TransactionStatus.REFUNDED);

        JsonNode body = getLink(link.getId());
        Assertions.assertEquals(2, body.get("currentPaymentsCount").asInt(),
                "two payments happened; returning one of them does not unmake it (Р-49)");
        Assertions.assertEquals(1, body.get("refundedPaymentsCount").asInt());
    }

    // 2. Главный тест: возврат не освобождает слот

    // Разрешено 3, платежей было два, один возвращён: ссылка обязана принять ровно один платёж,
    // а не два — за жизнь она соберёт 3, это и значит «разрешено 3». До P2-16 возвращённый слот
    // открывался заново и ссылка собирала 4.
    @Test
    void refundDoesNotFreeASlot_linkAcceptsOneMorePayment_notTwo() throws Exception {
        PaymentLink link = seedLink(UsageType.MULTIPLE, 3);
        seedTransaction(link, TransactionStatus.SUCCESS);
        seedTransaction(link, TransactionStatus.REFUNDED);

        // Третий слот действительно свободен.
        settleOnePaymentThroughProvider(link.getId());

        PaymentLink reloaded = paymentLinkRepository.findById(link.getId()).orElseThrow();
        Assertions.assertEquals(PaymentLinkStatus.COMPLETED, reloaded.getStatus(),
                "the third payment is the last one: 3 uses out of 3");

        // Четвёртый платёж отклонён: ссылка закрылась третьим. До P2-16 она здесь не закрывалась
        // вовсе — возвращённый платёж исчезал из счёта.
        openLink(link.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("COMPLETED")));

        // Колонка несёт то же число, что отдаёт API.
        JsonNode body = getLink(link.getId());
        Assertions.assertEquals(3, body.get("currentPaymentsCount").asInt());
        Assertions.assertEquals(3, reloaded.getCurrentPaymentsCount());
    }

    // Арифметика слотов на ссылке, которая ещё ACTIVE, хотя все слоты заняты: OpenLinkService
    // обязан считать возвращённый платёж занятым слотом и отказать до обращения к эквайеру.
    // До P2-16 такое открытие проходило. Ссылка намеренно остаётся ACTIVE в базе: записи пути
    // открытия откатываются вместе с отказом, см. openAndBuildRedirect.
    @Test
    void openOnActiveLink_withARefundedPaymentAtTheLimit_isRefused() throws Exception {
        PaymentLink link = seedLink(UsageType.MULTIPLE, 2);
        seedTransaction(link, TransactionStatus.SUCCESS);
        seedTransaction(link, TransactionStatus.REFUNDED);

        openLink(link.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("usage limit")));

        verify(acquiringClient, never()).createEcomOrder(any(), anyString(), anyString(), any(), anyString());
    }

    // 3. Частичный возврат — тоже использование

    // Безусловно неверный случай старой арифметики: вернули 1.00 из 100.00 — и весь платёж
    // переставал считаться, хотя 99.00 остались у мерчанта. Прогон через настоящий refund,
    // чтобы строка получила PARTIALLY_REFUNDED так же, как в проде.
    @Test
    void partialRefund_leavesTheUseCounted() throws Exception {
        PaymentLink link = seedLink(UsageType.MULTIPLE, 2);
        Transaction paid = seedTransaction(link, TransactionStatus.SUCCESS);

        refundThroughApi(paid, new BigDecimal("1.00"));

        Assertions.assertEquals(TransactionStatus.PARTIALLY_REFUNDED,
                transactionRepository.findById(paid.getId()).orElseThrow().getStatus());
        JsonNode body = getLink(link.getId());
        Assertions.assertEquals(1, body.get("currentPaymentsCount").asInt(),
                "1 of 100 went back, 99 stayed with the merchant — the payment happened");
        Assertions.assertEquals(1, body.get("refundedPaymentsCount").asInt(),
                "a partial refund shows up in the refunded counter too (Р-50)");
    }

    // 4, 5. refundedPaymentsCount

    @Test
    void refundedPaymentsCount_countsFullAndPartialRefundsAlike() throws Exception {
        PaymentLink link = seedLink(UsageType.MULTIPLE, 5);
        seedTransaction(link, TransactionStatus.SUCCESS);
        seedTransaction(link, TransactionStatus.REFUNDED);
        seedTransaction(link, TransactionStatus.PARTIALLY_REFUNDED);

        JsonNode body = getLink(link.getId());
        Assertions.assertEquals(3, body.get("currentPaymentsCount").asInt());
        Assertions.assertEquals(2, body.get("refundedPaymentsCount").asInt());
    }

    @Test
    void noRefunds_refundedPaymentsCountIsZero() throws Exception {
        PaymentLink link = seedLink(UsageType.MULTIPLE, 5);
        seedTransaction(link, TransactionStatus.SUCCESS);

        JsonNode body = getLink(link.getId());
        Assertions.assertEquals(0, body.get("refundedPaymentsCount").asInt(),
                "no refunds happened, and the field says so rather than being absent");
    }

    // 6. Регрессия: возврат не воскрешает завершённую ссылку

    // Одноразовая ссылка оплачена и стала COMPLETED, затем платёж возвращён целиком. Ссылка
    // остаётся COMPLETED и закрытой намеренно (Р-49): возврат отдаёт деньги, а не новую попытку.
    @Test
    void refundedSingleUseLink_staysCompletedAndDoesNotReopen() throws Exception {
        PaymentLink link = seedLink(UsageType.SINGLE, null);
        settleOnePaymentThroughProvider(link.getId());
        Transaction paid = transactionRepository.findByLinkIdOrderByCreatedAtDesc(link.getId()).getFirst();

        refundThroughApi(paid, AMOUNT);

        PaymentLink reloaded = paymentLinkRepository.findById(link.getId()).orElseThrow();
        Assertions.assertEquals(PaymentLinkStatus.COMPLETED, reloaded.getStatus(),
                "the refund must not recompute the link status");
        openLink(link.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("COMPLETED")));

        // Колонка и ответ сходятся после всего пути: оплачено раз, возвращено раз.
        JsonNode body = getLink(link.getId());
        Assertions.assertEquals(1, body.get("currentPaymentsCount").asInt());
        Assertions.assertEquals(1, body.get("refundedPaymentsCount").asInt());
        Assertions.assertEquals(1, reloaded.getCurrentPaymentsCount(),
                "current_payments_count in the database must match the API");
    }

    // 7. Регрессия: живой холд занимает слот (P1-6)

    // SLOT_OCCUPYING_STATUSES выводится из PAID_STATUSES; тест сторожит слагаемое AUTHORIZED:
    // живой холд держит слот, иначе по ссылке пройдёт второе списание.
    @Test
    void authorizedHold_stillOccupiesASlot() throws Exception {
        PaymentLink link = seedLink(UsageType.MULTIPLE, 1);
        seedTransaction(link, TransactionStatus.AUTHORIZED);

        openLink(link.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Payment link has an authorized payment awaiting capture")));

        verify(acquiringClient, never()).createEcomOrder(any(), anyString(), anyString(), any(), anyString());
    }

    // 8. Понижение maxPayments учитывает возвращённые платежи

    @Test
    void lowerMaxPayments_belowTheUses_isRefusedCountingRefundedPayments() throws Exception {
        PaymentLink link = seedLink(UsageType.MULTIPLE, 5);
        seedTransaction(link, TransactionStatus.SUCCESS);
        seedTransaction(link, TransactionStatus.REFUNDED);

        ObjectNode update = objectMapper.createObjectNode();
        update.put("maxPayments", 1);
        patchLink(link.getId(), update)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("1")))
                .andExpect(jsonPath("$.message", containsString("2")));
        Assertions.assertEquals(5, paymentLinkRepository.findById(link.getId()).orElseThrow().getMaxPayments());

        // Равное числу использований разрешено: так ссылку закрывают на уже собранном.
        update.put("maxPayments", 2);
        patchLink(link.getId(), update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPayments", is(2)))
                .andExpect(jsonPath("$.currentPaymentsCount", is(2)))
                .andExpect(jsonPath("$.refundedPaymentsCount", is(1)));
    }

    // 9. Колонка и ответ говорят одно число

    // Колонка переписывается на каждом расчёте счётом по PAID_STATUSES, а возврат не трогает ни
    // один статус из этого набора: база и API совпадают на всём пути оплата → возврат → оплата,
    // причём refund() ссылку не пишет вовсе.
    @Test
    void columnAndResponse_agreeThroughPaymentsAndRefunds() throws Exception {
        PaymentLink link = seedLink(UsageType.MULTIPLE, 3);

        settleOnePaymentThroughProvider(link.getId());
        Transaction first = transactionRepository.findByLinkIdOrderByCreatedAtDesc(link.getId()).getFirst();
        assertColumnMatchesResponse(link.getId(), 1);

        refundThroughApi(first, AMOUNT);
        assertColumnMatchesResponse(link.getId(), 1);

        settleOnePaymentThroughProvider(link.getId());
        assertColumnMatchesResponse(link.getId(), 2);
    }

    private void assertColumnMatchesResponse(UUID linkId, int expected) throws Exception {
        JsonNode body = getLink(linkId);
        Assertions.assertEquals(expected, body.get("currentPaymentsCount").asInt(), "API count");
        Assertions.assertEquals(expected,
                paymentLinkRepository.findById(linkId).orElseThrow().getCurrentPaymentsCount(),
                "current_payments_count column");
    }

    // Фикстуры

    private PaymentLink seedLink(UsageType usageType, Integer maxPayments) {
        return paymentLinkRepository.save(PaymentLink.builder()
                .providerReference("RID-" + UUID.randomUUID().toString().substring(0, 8))
                .merchantOrderId("ORDER-" + UUID.randomUUID().toString().substring(0, 8))
                .terminalId(TERMINAL_ID)
                .amount(AMOUNT)
                .currency("AZN")
                .description("Refund usage fixture")
                .paymentType(PaymentType.SMS)
                .usageType(usageType)
                .maxPayments(maxPayments)
                .currentPaymentsCount(0)
                .status(PaymentLinkStatus.ACTIVE)
                .build());
    }

    private Transaction seedTransaction(PaymentLink link, TransactionStatus status) {
        return transactionRepository.save(Transaction.builder()
                .link(link)
                .merchantRid(UUID.randomUUID())
                .providerOrderId("ORD-" + UUID.randomUUID())
                .providerPassword("provider-password")
                .amount(AMOUNT)
                .refundedAmount(BigDecimal.ZERO)
                .status(status)
                .build());
    }

    private ResultActions openLink(UUID id) throws Exception {
        return mockMvc.perform(get("/api/v1/payment-links/{id}/open", id));
    }

    private JsonNode getLink(UUID id) throws Exception {
        String body = mockMvc.perform(get("/api/v1/payment-links/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private ResultActions patchLink(UUID id, ObjectNode body) throws Exception {
        return mockMvc.perform(patch("/api/v1/payment-links/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, headToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    // Открывает ссылку и закрывает свежую попытку опросом статуса с ответом FullyPaid.
    private void settleOnePaymentThroughProvider(UUID linkId) throws Exception {
        openLink(linkId).andExpect(status().isFound());

        Transaction attempt = transactionRepository.findByLinkIdOrderByCreatedAtDesc(linkId).getFirst();
        when(acquiringClient.getOrderStatus(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("status", "FullyPaid"));
        mockMvc.perform(get("/api/v1/transactions/{identifier}/status", attempt.getProviderOrderId())
                        .header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")));
    }

    // Возврат через настоящий endpoint, эквайер подтверждает (форма контракта §5.7).
    private void refundThroughApi(Transaction tx, BigDecimal amount) throws Exception {
        String tag = tx.getId().toString().substring(0, 8);
        when(acquiringClient.refund(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new MoneyOperationResult("AC-" + tag, "TA-" + tag, "RID-" + tag,
                        Map.of("tran", Map.of("approvalCode", "AC-" + tag,
                                "match", Map.of("tranActionId", "TA-" + tag, "ridByPmo", "RID-" + tag)))));

        ObjectNode body = objectMapper.createObjectNode();
        body.put("amount", amount);
        body.put("reason", "Customer request");
        mockMvc.perform(post("/api/v1/transactions/{id}/refund", tx.getId())
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }
}
