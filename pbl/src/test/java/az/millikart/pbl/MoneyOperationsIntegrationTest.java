package az.millikart.pbl;

import az.millikart.common.testing.PostgresTestContainer;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.PaymentOutcomeUnknownException;
import az.millikart.common.security.JwtProvider;
import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.domain.Transaction;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.pbl.domain.UsageType;
import az.millikart.pbl.provider.AcquiringClient;
import az.millikart.pbl.provider.TxpgAcquiringClient;
import az.millikart.pbl.provider.dto.MoneyOperationResult;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.retry.annotation.Retry;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// P0-7 + P0-8 + P1-8b: capture и refund не должны списать или выплатить дважды при сбоях шлюза,
// частичный capture не должен становиться способом вернуть неснятые деньги, а операция считается
// выполненной только после подтверждения эквайера. StubAcquiringClient всегда отвечает успехом и
// FullyPaid — поэтому здесь провайдер это @MockBean и каждый тест сам диктует ответ эквайера.
//
// На настоящей PostgreSQL, а не на H2: почти всё, что здесь проверяется, лежит в
// `provider_response` — колонке типа jsonb. След возврата, метка списания и история операции
// читаются и пишутся через неё, а у H2 под `@JdbcTypeCode(SqlTypes.JSON)` свой тип со своим
// поведением. Сюда же суммы: точность BigDecimal при частичном списании — вопрос к настоящему
// numeric, а не к его эмуляции.
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class MoneyOperationsIntegrationTest {

    private static final int TERMINAL_ID = 123456789;
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    // Фикстуры P0-8: авторизовано 1500, снято 500 — пара, на которой раньше возвращалось 1500.
    private static final BigDecimal AUTHORIZED_AMOUNT = new BigDecimal("1500.00");
    private static final BigDecimal CAPTURED_AMOUNT = new BigDecimal("500.00");

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

    @MockBean
    private AcquiringClient acquiringClient;

    // COMPANY_HEAD владеет test-company, ему разрешены и capture, и refund.
    private String headToken;

    @BeforeEach
    void cleanUp() {
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
    }

    // --- capture --------------------------------------------------------------------------

    // Главный тест против двойного списания: SUCCESS раньше принимался на вход completeDms, и одну
    // авторизацию можно было снимать снова и снова. Провайдера не должны даже дёрнуть.
    @Test
    void completeDms_alreadyCaptured_returns400() throws Exception {
        Transaction captured = transaction("DONE", TransactionStatus.SUCCESS);

        mockMvc.perform(capture(captured, AMOUNT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Transaction has already been captured")));

        verify(acquiringClient, never()).completeDms(anyString(), anyString(), anyString(), anyString(), any());
        verify(acquiringClient, never()).getOrderStatus(anyString(), anyString(), anyString(), anyString());
        Assertions.assertEquals(TransactionStatus.SUCCESS, statusOf(captured));
    }

    // PENDING нельзя отвергать сразу: страница плательщика опрашивает эквайера ровно один раз
    // (P0-2), поэтому холд, поставленный после опроса, у нас всё ещё PENDING. Один опрос решает.
    @Test
    void completeDms_pendingButAuthorizedAtProvider_succeeds() throws Exception {
        Transaction pending = transaction("LATE-HOLD", TransactionStatus.PENDING);
        when(acquiringClient.getOrderStatus(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("status", "Authorized"));
        when(acquiringClient.completeDms(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(confirmed("CAP"));

        mockMvc.perform(capture(pending, AMOUNT))
                .andExpect(status().isOk());

        verify(acquiringClient, times(1)).completeDms(anyString(), anyString(), anyString(), anyString(), any());
        Assertions.assertEquals(TransactionStatus.SUCCESS, statusOf(pending));
    }

    // У эквайера всё ещё не авторизовано: отказ, клиринг не отправляем.
    @Test
    void completeDms_stillPendingAtProvider_returns400() throws Exception {
        Transaction pending = transaction("NO-HOLD", TransactionStatus.PENDING);
        // "Preparing" — заказ есть, но карту так и не ввели.
        when(acquiringClient.getOrderStatus(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("status", "Preparing"));

        mockMvc.perform(capture(pending, AMOUNT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("has not been authorized by the acquirer yet")));

        verify(acquiringClient, never()).completeDms(anyString(), anyString(), anyString(), anyString(), any());
        Assertions.assertEquals(TransactionStatus.PENDING, statusOf(pending));
    }

    // Оборванный capture мог уже пройти. 502 говорит об этом; 400 позвал бы мерчанта повторить.
    @Test
    void completeDms_providerTimeout_returns502AndKeepsStatus() throws Exception {
        Transaction authorized = transaction("HOLD", TransactionStatus.AUTHORIZED);
        when(acquiringClient.completeDms(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new PaymentOutcomeUnknownException(
                        "No response from the acquirer for the completeDms: Read timed out"));

        mockMvc.perform(capture(authorized, AMOUNT))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message", containsString("No confirmation received from the acquirer")));

        Assertions.assertEquals(TransactionStatus.AUTHORIZED, statusOf(authorized),
                "an unconfirmed capture must leave the local status untouched");
        Assertions.assertEquals(PaymentLinkStatus.ACTIVE, linkStatusOf(authorized));
    }

    // --- P0-8: частичный capture -----------------------------------------------------------

    // Запрос capture раньше шёл вообще без суммы, поэтому эквайер всегда снимал весь холд, а API
    // при этом принимал — и возвращал — меньшую цифру.
    @Test
    void completeDms_partialAmount_isSentToAcquirer() throws Exception {
        Transaction authorized = dmsTransaction("PARTIAL", TransactionStatus.AUTHORIZED, AUTHORIZED_AMOUNT, null);
        when(acquiringClient.completeDms(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(confirmed("CAP"));

        mockMvc.perform(capture(authorized, CAPTURED_AMOUNT))
                .andExpect(status().isOk());

        ArgumentCaptor<BigDecimal> sent = ArgumentCaptor.forClass(BigDecimal.class);
        verify(acquiringClient).completeDms(anyString(), anyString(), anyString(), anyString(), sent.capture());
        Assertions.assertEquals(0, CAPTURED_AMOUNT.compareTo(sent.getValue()),
                "the acquirer must receive the requested 500, not the authorized 1500, got: " + sent.getValue());
    }

    // amount хранит авторизованную цифру — это запись о том, что было захолдировано.
    @Test
    void completeDms_partialAmount_isStoredAsCapturedAmount() throws Exception {
        Transaction authorized = dmsTransaction("PARTIAL-STORE", TransactionStatus.AUTHORIZED, AUTHORIZED_AMOUNT, null);
        when(acquiringClient.completeDms(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(confirmed("CAP"));

        mockMvc.perform(capture(authorized, CAPTURED_AMOUNT))
                .andExpect(status().isOk());

        Transaction captured = reload(authorized);
        Assertions.assertEquals(0, CAPTURED_AMOUNT.compareTo(captured.getCapturedAmount()),
                "capturedAmount must hold what was cleared, got: " + captured.getCapturedAmount());
        Assertions.assertEquals(0, AUTHORIZED_AMOUNT.compareTo(captured.getAmount()),
                "amount must stay the authorized figure, got: " + captured.getAmount());
        Assertions.assertEquals(TransactionStatus.SUCCESS, captured.getStatus(),
                "a partial capture still settles the transaction; there is no PARTIALLY_CAPTURED state");
    }

    // Снять больше, чем захолдировано, нельзя; отказываем здесь, а не отдаём это шлюзу.
    @Test
    void completeDms_amountAboveAuthorized_returns400() throws Exception {
        Transaction authorized = dmsTransaction("ABOVE", TransactionStatus.AUTHORIZED, AUTHORIZED_AMOUNT, null);

        mockMvc.perform(capture(authorized, new BigDecimal("2000.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Capture amount exceeds the authorized amount")));

        verify(acquiringClient, never()).completeDms(anyString(), anyString(), anyString(), anyString(), any());
        Assertions.assertEquals(TransactionStatus.AUTHORIZED, statusOf(authorized));
        Assertions.assertNull(reload(authorized).getCapturedAmount());
    }

    // Деньги нельзя молча округлять по дороге к шлюзу, поэтому третий знак отвергается на границе,
    // а не доезжает до RoundingMode.UNNECESSARY внутри клиента.
    @Test
    void completeDms_amountWithThreeDecimals_returns400() throws Exception {
        Transaction authorized = dmsTransaction("SCALE", TransactionStatus.AUTHORIZED, AUTHORIZED_AMOUNT, null);

        mockMvc.perform(capture(authorized, new BigDecimal("500.005")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("two decimal places")));

        verify(acquiringClient, never()).completeDms(anyString(), anyString(), anyString(), anyString(), any());
        Assertions.assertEquals(TransactionStatus.AUTHORIZED, statusOf(authorized));
    }

    // Регресс-сторож обычного пути: снимаем весь холд, больше ничего не меняется.
    @Test
    void completeDms_fullAmount_stillWorks() throws Exception {
        Transaction authorized = dmsTransaction("FULL", TransactionStatus.AUTHORIZED, AUTHORIZED_AMOUNT, null);
        when(acquiringClient.completeDms(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(confirmed("CAP"));

        mockMvc.perform(capture(authorized, AUTHORIZED_AMOUNT))
                .andExpect(status().isOk());

        ArgumentCaptor<BigDecimal> sent = ArgumentCaptor.forClass(BigDecimal.class);
        verify(acquiringClient).completeDms(anyString(), anyString(), anyString(), anyString(), sent.capture());
        Assertions.assertEquals(0, AUTHORIZED_AMOUNT.compareTo(sent.getValue()));

        Transaction captured = reload(authorized);
        Assertions.assertEquals(TransactionStatus.SUCCESS, captured.getStatus());
        Assertions.assertEquals(0, AUTHORIZED_AMOUNT.compareTo(captured.getCapturedAmount()));
    }

    // --- refund ---------------------------------------------------------------------------

    // Главный тест против двойного возврата. Мерчанту говорят "исход неизвестен" (502), а не "не
    // получилось" (400), и локально ничего не сдвинулось — записанная сумма возврата не может
    // разъехаться с тем, что эквайер реально выплатил.
    @Test
    void refund_providerTimeout_returns502AndKeepsRefundedAmount() throws Exception {
        Transaction settled = transaction("PAID", TransactionStatus.SUCCESS);
        when(acquiringClient.refund(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new PaymentOutcomeUnknownException(
                        "No response from the acquirer for the refund: Read timed out"));

        mockMvc.perform(refund(settled, new BigDecimal("40.00")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message", containsString("Check the transaction status before retrying")));

        Transaction untouched = reload(settled);
        Assertions.assertEquals(TransactionStatus.SUCCESS, untouched.getStatus());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(untouched.getRefundedAmount()),
                "an unconfirmed refund must not be recorded locally");
    }

    // Определённый отказ шлюза остаётся простым 400: ничего не сдвинулось, повтор безопасен.
    @Test
    void refund_providerRejects_returns400() throws Exception {
        Transaction settled = transaction("PAID", TransactionStatus.SUCCESS);
        when(acquiringClient.refund(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new BusinessException("Acquirer error: Refund amount exceeds cleared amount"));

        mockMvc.perform(refund(settled, new BigDecimal("40.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Acquirer error")));

        Transaction untouched = reload(settled);
        Assertions.assertEquals(TransactionStatus.SUCCESS, untouched.getStatus());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(untouched.getRefundedAmount()));
    }

    // Выдуманный номер возврата, которого нет ни в одной системе, хуже, чем никакого. Эквайер
    // подтвердил (ridByPmo есть), но tranActionId не прислал: refundId остаётся пустым,
    // подтверждение несёт acquirerReference.
    @Test
    void refund_providerReturnsNoTranActionId_responseHasNullRefundId() throws Exception {
        Transaction settled = transaction("PAID", TransactionStatus.SUCCESS);
        when(acquiringClient.refund(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new MoneyOperationResult(null, null, "220613334596244733",
                        Map.of("tran", Map.of("match", Map.of("ridByPmo", "220613334596244733")))));

        String body = mockMvc.perform(refund(settled, new BigDecimal("40.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PARTIALLY_REFUNDED")))
                .andExpect(jsonPath("$.acquirerReference", is("220613334596244733")))
                .andReturn().getResponse().getContentAsString();

        JsonNode refundId = objectMapper.readTree(body).get("refundId");
        Assertions.assertTrue(refundId == null || refundId.isNull(),
                "refundId must stay empty rather than be fabricated, got: " + refundId);
    }

    // --- P1-8b: подтверждение эквайера сохраняется, его отсутствие останавливает операцию ----

    // Подтверждённый возврат отдаёт ссылку эквайера и оставляет след в providerResponse.
    @Test
    void refund_confirmed_reportsAcquirerReferenceAndRecordsTheRefund() throws Exception {
        Transaction settled = transaction("PAID", TransactionStatus.SUCCESS);
        when(acquiringClient.refund(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(confirmed("REF-A"));

        mockMvc.perform(refund(settled, new BigDecimal("40.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PARTIALLY_REFUNDED")))
                .andExpect(jsonPath("$.refundId", is("TA-REF-A")))
                .andExpect(jsonPath("$.acquirerReference", is("RID-REF-A")))
                .andExpect(jsonPath("$.approvalCode", is("AC-REF-A")));

        List<Map<String, Object>> refunds = refundsOf(settled);
        Assertions.assertEquals(1, refunds.size(), "one refund, one record: " + refunds);
        Map<String, Object> record = refunds.getFirst();
        Assertions.assertEquals("RID-REF-A", record.get("ridByPmo"));
        Assertions.assertEquals("TA-REF-A", record.get("tranActionId"));
        Assertions.assertEquals("AC-REF-A", record.get("approvalCode"));
        Assertions.assertEquals("40.00", record.get("amount"));
        Assertions.assertNotNull(record.get("at"));
    }

    // Частичные возвраты накапливаются: два возврата — две записи, у каждой свои идентификаторы.
    @Test
    void refund_twoPartialRefunds_recordsBothWithDistinctIdentifiers() throws Exception {
        Transaction settled = transaction("PAID", TransactionStatus.SUCCESS);
        when(acquiringClient.refund(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(confirmed("FIRST"))
                .thenReturn(confirmed("SECOND"));

        mockMvc.perform(refund(settled, new BigDecimal("30.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acquirerReference", is("RID-FIRST")));
        mockMvc.perform(refund(settled, new BigDecimal("20.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acquirerReference", is("RID-SECOND")));

        List<Map<String, Object>> refunds = refundsOf(settled);
        Assertions.assertEquals(2, refunds.size(), "two refunds, two records: " + refunds);
        Assertions.assertEquals("RID-FIRST", refunds.get(0).get("ridByPmo"));
        Assertions.assertEquals("30.00", refunds.get(0).get("amount"));
        Assertions.assertEquals("RID-SECOND", refunds.get(1).get("ridByPmo"));
        Assertions.assertEquals("20.00", refunds.get(1).get("amount"));
        Assertions.assertNotEquals(refunds.get(0).get("tranActionId"), refunds.get(1).get("tranActionId"));

        Transaction reloaded = reload(settled);
        Assertions.assertEquals(TransactionStatus.PARTIALLY_REFUNDED, reloaded.getStatus());
        Assertions.assertEquals(0, new BigDecimal("50.00").compareTo(reloaded.getRefundedAmount()));
    }

    // Эквайер ответил 200, но без tran.match.ridByPmo: клиент превращает это в
    // PaymentOutcomeUnknownException (Р-23), мерчант видит 502 и локально ничего не двигается —
    // деньги могли уйти обратно, а могли и нет, и записанный возврат был бы догадкой.
    @Test
    void refund_providerAnswersWithoutConfirmation_returns502AndKeepsRefundedAmount() throws Exception {
        Transaction settled = transaction("PAID", TransactionStatus.SUCCESS);
        when(acquiringClient.refund(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new PaymentOutcomeUnknownException(
                        "Acquirer accepted the refund but did not confirm it: the response has no tran.match.ridByPmo"));

        mockMvc.perform(refund(settled, new BigDecimal("40.00")))
                .andExpect(status().isBadGateway());

        Transaction untouched = reload(settled);
        Assertions.assertEquals(TransactionStatus.SUCCESS, untouched.getStatus());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(untouched.getRefundedAmount()),
                "an unconfirmed refund must not be recorded locally");
        Assertions.assertTrue(refundsOf(settled).isEmpty(), "no trail for a refund that was not confirmed");
    }

    // То же для capture: 502, статус остаётся AUTHORIZED, снятое никуда не записывается.
    @Test
    void completeDms_providerAnswersWithoutConfirmation_returns502AndKeepsStatus() throws Exception {
        Transaction authorized = transaction("HOLD", TransactionStatus.AUTHORIZED);
        when(acquiringClient.completeDms(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new PaymentOutcomeUnknownException(
                        "Acquirer accepted the completeDms but did not confirm it: the response has no tran.match.ridByPmo"));

        mockMvc.perform(capture(authorized, AMOUNT))
                .andExpect(status().isBadGateway());

        Transaction untouched = reload(authorized);
        Assertions.assertEquals(TransactionStatus.AUTHORIZED, untouched.getStatus());
        Assertions.assertNull(untouched.getCapturedAmount(), "an unconfirmed capture must not be recorded");
        Assertions.assertTrue(untouched.getProviderResponse() == null
                        || !untouched.getProviderResponse().containsKey("mpCapture"),
                "no capture evidence for a capture that was not confirmed");
        Assertions.assertEquals(PaymentLinkStatus.ACTIVE, linkStatusOf(authorized));
    }

    // Подтверждённый capture оставляет след в mpCapture. Мерчант шлёт голое 500 (scale 0), а в
    // записи стоит "500.00" — та же цифра, что ушла эквайеру, а не scale из JSON мерчанта.
    @Test
    void completeDms_confirmed_recordsCaptureEvidence() throws Exception {
        Transaction authorized = dmsTransaction("CAP-EVIDENCE", TransactionStatus.AUTHORIZED, AUTHORIZED_AMOUNT, null);
        when(acquiringClient.completeDms(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(confirmed("CAP"));

        mockMvc.perform(capture(authorized, new BigDecimal("500")))
                .andExpect(status().isOk());

        Transaction captured = reload(authorized);
        Assertions.assertEquals(TransactionStatus.SUCCESS, captured.getStatus());
        Object evidence = captured.getProviderResponse().get("mpCapture");
        Assertions.assertInstanceOf(Map.class, evidence, "mpCapture must be present: " + captured.getProviderResponse());
        Map<?, ?> record = (Map<?, ?>) evidence;
        Assertions.assertEquals("RID-CAP", record.get("ridByPmo"));
        Assertions.assertEquals("TA-CAP", record.get("tranActionId"));
        Assertions.assertEquals("AC-CAP", record.get("approvalCode"));
        Assertions.assertEquals("500.00", record.get("amount"));
        Assertions.assertNotNull(record.get("at"));
        // Сырое тело по-прежнему домешивается, как и раньше.
        Assertions.assertTrue(captured.getProviderResponse().containsKey("tran"));
    }

    // --- P0-8: потолок возврата следует за снятой суммой ------------------------------------

    // Главный тест этого изменения. Авторизовано 1500, снято 500, остальная 1000 со счёта не
    // уходила. С потолком по amount — как было раньше — возврат 501 проходит и выплачивает деньги,
    // которые никогда не списывались.
    @Test
    void refund_afterPartialCapture_cannotExceedCapturedAmount() throws Exception {
        Transaction captured = dmsTransaction("PARTIAL-PAID", TransactionStatus.SUCCESS,
                AUTHORIZED_AMOUNT, CAPTURED_AMOUNT);

        mockMvc.perform(refund(captured, new BigDecimal("501.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("exceeds the captured amount")));

        verify(acquiringClient, never()).refund(anyString(), anyString(), anyString(), anyString(), any());
        Transaction untouched = reload(captured);
        Assertions.assertEquals(TransactionStatus.SUCCESS, untouched.getStatus());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(untouched.getRefundedAmount()));
    }

    // Вернуть всё, что реально было списано, — это полный возврат, а не частичный.
    @Test
    void refund_afterPartialCapture_fullCapturedAmount_marksRefunded() throws Exception {
        Transaction captured = dmsTransaction("PARTIAL-FULLBACK", TransactionStatus.SUCCESS,
                AUTHORIZED_AMOUNT, CAPTURED_AMOUNT);
        when(acquiringClient.refund(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(confirmed("REF-1"));

        mockMvc.perform(refund(captured, CAPTURED_AMOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REFUNDED")));

        Assertions.assertEquals(TransactionStatus.REFUNDED, statusOf(captured),
                "500 refunded out of 500 captured is REFUNDED, not PARTIALLY_REFUNDED");
    }

    // SMS-платежи не проходят стадию capture, capturedAmount у них пуст, и потолок обязан падать
    // обратно на авторизованную сумму. Их поведение не должно измениться совсем.
    @Test
    void refund_smsTransactionWithoutCapture_usesAuthorizedAmount() throws Exception {
        Transaction settled = smsTransaction("SMS-PAID", TransactionStatus.SUCCESS, AUTHORIZED_AMOUNT);
        Assertions.assertNull(settled.getCapturedAmount(), "an SMS payment has no capture stage");
        when(acquiringClient.refund(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(confirmed("REF-2"));

        // Копейка сверх авторизованной суммы всё так же отвергается — потолок ровно amount.
        mockMvc.perform(refund(settled, new BigDecimal("1500.01")))
                .andExpect(status().isBadRequest());
        verify(acquiringClient, never()).refund(anyString(), anyString(), anyString(), anyString(), any());

        mockMvc.perform(refund(settled, AUTHORIZED_AMOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REFUNDED")));

        Assertions.assertEquals(TransactionStatus.REFUNDED, statusOf(settled));
    }

    // --- P0-7: никаких повторов на денежных операциях ---------------------------------------

    // Неудавшийся capture уходит ровно один раз. С @Retry это было бы три попытки и до трёх
    // списаний с держателя карты на один записанный capture.
    @Test
    void completeDms_providerFails_isNotRetried() throws Exception {
        Transaction authorized = transaction("HOLD", TransactionStatus.AUTHORIZED);
        when(acquiringClient.completeDms(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new PaymentOutcomeUnknownException("Read timed out"));

        mockMvc.perform(capture(authorized, AMOUNT))
                .andExpect(status().isBadGateway());

        verify(acquiringClient, times(1)).completeDms(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void refund_providerFails_isNotRetried() throws Exception {
        Transaction settled = transaction("PAID", TransactionStatus.SUCCESS);
        when(acquiringClient.refund(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new PaymentOutcomeUnknownException("Read timed out"));

        mockMvc.perform(refund(settled, new BigDecimal("40.00")))
                .andExpect(status().isBadGateway());

        verify(acquiringClient, times(1)).refund(anyString(), anyString(), anyString(), anyString(), any());
    }

    // Тесты выше идут против мока, где аспектов resilience4j нет по построению. Этот сторожит
    // реальный клиент: @Retry допустим только на двух вызовах, повтор которых не превращается в
    // деньги, — создание заказа (дубль неоплаченного безвреден, ridByMerchant свой) и чтение статуса.
    @Test
    void retryIsDeclaredOnlyOnIdempotentProviderCalls() {
        Set<String> retried = Arrays.stream(TxpgAcquiringClient.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Retry.class))
                .map(Method::getName)
                .collect(Collectors.toSet());

        Assertions.assertEquals(Set.of("createEcomOrder", "getOrderStatus"), retried,
                "@Retry must never sit on completeDms or refund — they are not idempotent (P0-7)");
    }

    // --- фикстуры ---------------------------------------------------------------------------

    // --- история операции -----------------------------------------------------------------

    // Карточка операции показывает историю из того, что записано: заведение, списание холда и
    // каждый возврат — со своим временем и своей суммой. Блок был пуст, потому что ответ по
    // операции истории не нёс вовсе, а таблица связанных операций рисовала вместо неё две
    // выдуманные записи с одним временем.
    @Test
    void statusHistory_carriesCreationCaptureAndEveryRefund() throws Exception {
        Transaction authorized = dmsTransaction("HIST", TransactionStatus.AUTHORIZED,
                AUTHORIZED_AMOUNT, null);
        when(acquiringClient.completeDms(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(confirmed("CAP"));
        when(acquiringClient.refund(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(confirmed("REF-1"))
                .thenReturn(confirmed("REF-2"));

        mockMvc.perform(capture(authorized, CAPTURED_AMOUNT)).andExpect(status().isOk());
        mockMvc.perform(refund(authorized, new BigDecimal("200.00"))).andExpect(status().isOk());
        mockMvc.perform(refund(authorized, new BigDecimal("300.00"))).andExpect(status().isOk());

        JsonNode history = historyOf(authorized);
        Assertions.assertEquals(4, history.size(), "creation, capture and two refunds: " + history);

        Assertions.assertEquals("CREATED", history.get(0).get("type").asText());
        Assertions.assertEquals("PENDING", history.get(0).get("status").asText());

        Assertions.assertEquals("CAPTURED", history.get(1).get("type").asText());
        Assertions.assertEquals("SUCCESS", history.get(1).get("status").asText());
        Assertions.assertEquals(0, CAPTURED_AMOUNT.compareTo(history.get(1).get("amount").decimalValue()));
        Assertions.assertEquals("RID-CAP", history.get(1).get("acquirerReference").asText());

        // Возврат 200 из снятых 500 — ещё не полный, поэтому PARTIALLY_REFUNDED; следующий
        // добирает до 500 и закрывает операцию.
        Assertions.assertEquals("REFUNDED", history.get(2).get("type").asText());
        Assertions.assertEquals("PARTIALLY_REFUNDED", history.get(2).get("status").asText());
        Assertions.assertEquals("RID-REF-1", history.get(2).get("acquirerReference").asText());

        Assertions.assertEquals("REFUNDED", history.get(3).get("type").asText());
        Assertions.assertEquals("REFUNDED", history.get(3).get("status").asText());
        Assertions.assertEquals("RID-REF-2", history.get(3).get("acquirerReference").asText());

        // Порядок — по возрастанию времени, и время у каждого события своё, записанное.
        for (int i = 1; i < history.size(); i++) {
            Assertions.assertTrue(
                    !history.get(i).get("at").asText().isBlank()
                            && history.get(i).get("at").asText().compareTo(history.get(i - 1).get("at").asText()) >= 0,
                    "events must be ordered by their own recorded time: " + history);
        }
    }

    // Ничего не происходило — ничего и не выдумывается: одно событие, заведение операции.
    // Статус её при этом PENDING, то есть тем же событием и объяснён.
    @Test
    void statusHistory_forAnUntouchedTransaction_carriesOnlyItsCreation() throws Exception {
        Transaction pending = transaction("FRESH", TransactionStatus.PENDING);

        JsonNode history = historyOf(pending);
        Assertions.assertEquals(1, history.size(), "nothing happened yet: " + history);
        Assertions.assertEquals("CREATED", history.get(0).get("type").asText());
        Assertions.assertEquals("PENDING", history.get(0).get("status").asText());
        Assertions.assertTrue(history.get(0).get("amount").isNull(), "creation moves no money");
    }

    // У SMS-платежа стадии списания нет, и отдельной записи о переходе в SUCCESS никто не делал.
    // Состояние всё равно должно быть на экране: его закрывает событие STATUS со временем
    // последней записи строки — единственным временем, которое об этом переходе известно.
    @Test
    void statusHistory_forASettledSmsPayment_closesWithItsCurrentStatus() throws Exception {
        Transaction settled = smsTransaction("SMS-DONE", TransactionStatus.SUCCESS, AMOUNT);

        JsonNode history = historyOf(settled);
        Assertions.assertEquals(2, history.size(), "creation plus the state it ended in: " + history);
        Assertions.assertEquals("CREATED", history.get(0).get("type").asText());
        Assertions.assertEquals("STATUS", history.get(1).get("type").asText());
        Assertions.assertEquals("SUCCESS", history.get(1).get("status").asText());
        Assertions.assertTrue(history.get(1).get("acquirerReference").isNull(),
                "no acquirer reference is recorded for a transition nobody wrote down");
    }

    private JsonNode historyOf(Transaction tx) throws Exception {
        String body = mockMvc.perform(get("/api/v1/transactions/{id}", tx.getId())
                        .header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("statusHistory");
    }

    private MockHttpServletRequestBuilder capture(Transaction tx, BigDecimal amount) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("amount", amount);
        return post("/api/v1/transactions/{id}/complete", tx.getId())
                .header(HttpHeaders.AUTHORIZATION, headToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder refund(Transaction tx, BigDecimal amount) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("amount", amount);
        body.put("reason", "Customer request");
        return post("/api/v1/transactions/{id}/refund", tx.getId())
                .header(HttpHeaders.AUTHORIZATION, headToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private Transaction transaction(String key, TransactionStatus status) {
        return transaction(key, status, AMOUNT, null, PaymentType.DMS);
    }

    // Фикстура DMS с явной парой авторизовано/снято; capturedAmount может быть null.
    private Transaction dmsTransaction(String key, TransactionStatus status, BigDecimal amount, BigDecimal capturedAmount) {
        return transaction(key, status, amount, capturedAmount, PaymentType.DMS);
    }

    // Фикстура SMS: стадии capture нет, поэтому capturedAmount всегда пуст.
    private Transaction smsTransaction(String key, TransactionStatus status, BigDecimal amount) {
        return transaction(key, status, amount, null, PaymentType.SMS);
    }

    private Transaction transaction(String key, TransactionStatus status, BigDecimal amount,
                                    BigDecimal capturedAmount, PaymentType paymentType) {
        PaymentLink link = paymentLinkRepository.save(PaymentLink.builder()
                .providerReference("RID-" + key)
                .merchantOrderId(key)
                .terminalId(TERMINAL_ID)
                .amount(amount)
                .currency("AZN")
                .description("Fixture for " + key)
                .paymentType(paymentType)
                .usageType(UsageType.SINGLE)
                .currentPaymentsCount(0)
                .status(PaymentLinkStatus.ACTIVE)
                .build());

        return transactionRepository.save(Transaction.builder()
                .link(link)
                .ridByMerchant(UUID.randomUUID())
                .providerOrderId("ORD-" + key)
                .providerPassword("provider-password")
                .amount(amount)
                .capturedAmount(capturedAmount)
                .refundedAmount(BigDecimal.ZERO)
                .status(status)
                .build());
    }

    // Подтверждённый ответ exec-tran в форме §5.5–5.7; идентификаторы выводятся из tag, чтобы два
    // ответа в одном тесте различались.
    private static MoneyOperationResult confirmed(String tag) {
        String approvalCode = "AC-" + tag;
        String tranActionId = "TA-" + tag;
        String ridByPmo = "RID-" + tag;
        Map<String, Object> raw = Map.of("tran", Map.of(
                "approvalCode", approvalCode,
                "match", Map.of("tranActionId", tranActionId, "ridByPmo", ridByPmo)));
        return new MoneyOperationResult(approvalCode, tranActionId, ridByPmo, raw);
    }

    // Список mpRefunds перезагруженной транзакции; пустой, если его нет.
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> refundsOf(Transaction tx) {
        Map<String, Object> response = reload(tx).getProviderResponse();
        if (response == null || !(response.get("mpRefunds") instanceof List<?> refunds)) {
            return List.of();
        }
        return (List<Map<String, Object>>) refunds;
    }

    private Transaction reload(Transaction tx) {
        return transactionRepository.findById(tx.getId()).orElseThrow();
    }

    private TransactionStatus statusOf(Transaction tx) {
        return reload(tx).getStatus();
    }

    private PaymentLinkStatus linkStatusOf(Transaction tx) {
        return paymentLinkRepository.findById(tx.getLink().getId()).orElseThrow().getStatus();
    }
}
