package az.millikart.pbl;

import az.millikart.common.security.CredentialCipher;
import org.springframework.jdbc.core.JdbcTemplate;
import az.millikart.common.testing.PostgresIntegrationTest;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import az.millikart.common.security.UserPrincipal;
import az.millikart.pbl.dto.CompleteDmsRequest;
import az.millikart.pbl.service.PaymentLinkService;
import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.domain.Transaction;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.pbl.domain.UsageType;
import az.millikart.pbl.provider.AcquiringClient;
import az.millikart.pbl.provider.ProviderCredentials;
import az.millikart.pbl.provider.TxpgAcquiringClient;
import az.millikart.pbl.provider.dto.MoneyOperationResult;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.domain.TransactionRefund;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRefundRepository;
import az.millikart.pbl.repository.TransactionRepository;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.retry.annotation.Retry;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.PessimisticLockingFailureException;
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
@PostgresIntegrationTest
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CredentialCipher credentialCipher;

    @Autowired
    private TransactionRefundRepository transactionRefundRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PaymentLinkService paymentLinkService;

    @MockBean
    private AcquiringClient acquiringClient;

    // COMPANY_HEAD владеет test-company, ему разрешены и capture, и refund.
    private String headToken;

    @BeforeEach
    void cleanUp() {
        transactionRepository.deleteAll();
        paymentLinkRepository.deleteAll();
        terminalRepository.deleteAll();
        CompanyCredentialsFixture.seed(jdbcTemplate, credentialCipher, "test-company");

        terminalRepository.save(Terminal.builder()
                .id(TERMINAL_ID)
                .name("Test Terminal")
                .login("TerminalSys/Admin").terminalRid("TID-Admin")
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

        verify(acquiringClient, never()).completeDms(anyString(), any(), any());
        verify(acquiringClient, never()).getOrderStatus(anyString(), anyString(), any());
        Assertions.assertEquals(TransactionStatus.SUCCESS, statusOf(captured));
    }

    // PENDING нельзя отвергать сразу: страница плательщика опрашивает эквайера ровно один раз
    // (P0-2), поэтому холд, поставленный после опроса, у нас всё ещё PENDING. Один опрос решает.
    @Test
    void completeDms_pendingButAuthorizedAtProvider_succeeds() throws Exception {
        Transaction pending = transaction("LATE-HOLD", TransactionStatus.PENDING);
        when(acquiringClient.getOrderStatus(anyString(), anyString(), any()))
                .thenReturn(Map.of("status", "Authorized"));
        when(acquiringClient.completeDms(anyString(), any(), any()))
                .thenReturn(confirmed("CAP"));

        mockMvc.perform(capture(pending, AMOUNT))
                .andExpect(status().isOk());

        verify(acquiringClient, times(1)).completeDms(anyString(), any(), any());
        Assertions.assertEquals(TransactionStatus.SUCCESS, statusOf(pending));
    }

    // У эквайера всё ещё не авторизовано: отказ, клиринг не отправляем.
    @Test
    void completeDms_stillPendingAtProvider_returns400() throws Exception {
        Transaction pending = transaction("NO-HOLD", TransactionStatus.PENDING);
        // "Preparing" — заказ есть, но карту так и не ввели.
        when(acquiringClient.getOrderStatus(anyString(), anyString(), any()))
                .thenReturn(Map.of("status", "Preparing"));

        mockMvc.perform(capture(pending, AMOUNT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("has not been authorized by the acquirer yet")));

        verify(acquiringClient, never()).completeDms(anyString(), any(), any());
        Assertions.assertEquals(TransactionStatus.PENDING, statusOf(pending));
    }

    // Оборванный capture мог уже пройти. 502 говорит об этом; 400 позвал бы мерчанта повторить.
    @Test
    void completeDms_providerTimeout_returns502AndKeepsStatus() throws Exception {
        Transaction authorized = transaction("HOLD", TransactionStatus.AUTHORIZED);
        when(acquiringClient.completeDms(anyString(), any(), any()))
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
        when(acquiringClient.completeDms(anyString(), any(), any()))
                .thenReturn(confirmed("CAP"));

        mockMvc.perform(capture(authorized, CAPTURED_AMOUNT))
                .andExpect(status().isOk());

        ArgumentCaptor<BigDecimal> sent = ArgumentCaptor.forClass(BigDecimal.class);
        verify(acquiringClient).completeDms(anyString(), any(), sent.capture());
        Assertions.assertEquals(0, CAPTURED_AMOUNT.compareTo(sent.getValue()),
                "the acquirer must receive the requested 500, not the authorized 1500, got: " + sent.getValue());
    }

    // amount хранит авторизованную цифру — это запись о том, что было захолдировано.
    @Test
    void completeDms_partialAmount_isStoredAsCapturedAmount() throws Exception {
        Transaction authorized = dmsTransaction("PARTIAL-STORE", TransactionStatus.AUTHORIZED, AUTHORIZED_AMOUNT, null);
        when(acquiringClient.completeDms(anyString(), any(), any()))
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

        verify(acquiringClient, never()).completeDms(anyString(), any(), any());
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

        verify(acquiringClient, never()).completeDms(anyString(), any(), any());
        Assertions.assertEquals(TransactionStatus.AUTHORIZED, statusOf(authorized));
    }

    // Регресс-сторож обычного пути: снимаем весь холд, больше ничего не меняется.
    @Test
    void completeDms_fullAmount_stillWorks() throws Exception {
        Transaction authorized = dmsTransaction("FULL", TransactionStatus.AUTHORIZED, AUTHORIZED_AMOUNT, null);
        when(acquiringClient.completeDms(anyString(), any(), any()))
                .thenReturn(confirmed("CAP"));

        mockMvc.perform(capture(authorized, AUTHORIZED_AMOUNT))
                .andExpect(status().isOk());

        ArgumentCaptor<BigDecimal> sent = ArgumentCaptor.forClass(BigDecimal.class);
        verify(acquiringClient).completeDms(anyString(), any(), sent.capture());
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
        when(acquiringClient.refund(anyString(), any(), any()))
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
        when(acquiringClient.refund(anyString(), any(), any()))
                .thenThrow(new BusinessException("Acquirer error: Refund amount exceeds cleared amount"));

        mockMvc.perform(refund(settled, new BigDecimal("40.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Acquirer error")));

        Transaction untouched = reload(settled);
        Assertions.assertEquals(TransactionStatus.SUCCESS, untouched.getStatus());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(untouched.getRefundedAmount()));
    }

    // Р-93: к провайдеру ходят с кредами компании терминала. Их нет — отказ 400 до провайдера, а не 502:
    // деньги не двигались, и «исход неизвестен» здесь был бы неправдой.
    @Test
    void refundAndCapture_companyWithoutCredentials_return400BeforeTheProvider() throws Exception {
        Transaction settled = transaction("NO-CREDS-PAID", TransactionStatus.SUCCESS);
        Transaction held = transaction("NO-CREDS-HELD", TransactionStatus.AUTHORIZED);
        jdbcTemplate.update("UPDATE companies SET provider_login = NULL, provider_password = NULL WHERE id = 'test-company'");

        mockMvc.perform(refund(settled, new BigDecimal("40.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("has no acquirer credentials")));
        mockMvc.perform(capture(held, AMOUNT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("has no acquirer credentials")));

        verify(acquiringClient, never()).refund(anyString(), any(), any());
        verify(acquiringClient, never()).completeDms(anyString(), any(), any());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(reload(settled).getRefundedAmount()));
    }

    // Возврат уходит провайдеру с расшифрованными кредами компании, а не терминала (Р-93).
    @Test
    void refund_isSentWithTheCompanyCredentials() throws Exception {
        Transaction settled = transaction("COMPANY-CREDS", TransactionStatus.SUCCESS);
        when(acquiringClient.refund(anyString(), any(), any())).thenReturn(confirmed("REF"));

        mockMvc.perform(refund(settled, new BigDecimal("40.00")))
                .andExpect(status().isOk());

        verify(acquiringClient).refund(anyString(), eq(new ProviderCredentials(CompanyCredentialsFixture.loginOf("test-company"),
                        CompanyCredentialsFixture.passwordOf("test-company"))),
                any());
    }

    // Выдуманный номер возврата, которого нет ни в одной системе, хуже, чем никакого. Эквайер
    // подтвердил (ridByPmo есть), но tranActionId не прислал: refundId остаётся пустым,
    // подтверждение несёт acquirerReference.
    @Test
    void refund_providerReturnsNoTranActionId_responseHasNullRefundId() throws Exception {
        Transaction settled = transaction("PAID", TransactionStatus.SUCCESS);
        when(acquiringClient.refund(anyString(), any(), any()))
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
        when(acquiringClient.refund(anyString(), any(), any()))
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

        // Р-89: тот же возврат строкой — по её времени сводка главной вычитает возвраты периода.
        // Время то же, что в свидетельстве: сводка и история операции не расходятся.
        List<TransactionRefund> rows = transactionRefundRepository.findByTransactionIdOrderByRefundedAtAsc(settled.getId());
        Assertions.assertEquals(1, rows.size(), "one confirmed refund, one row: " + rows);
        Assertions.assertEquals(0, new BigDecimal("40.00").compareTo(rows.getFirst().getAmount()));
        Assertions.assertEquals("RID-REF-A", rows.getFirst().getRidByPmo());
        Assertions.assertEquals(Instant.parse((String) record.get("at")), rows.getFirst().getRefundedAt());
    }

    // Частичные возвраты накапливаются: два возврата — две записи, у каждой свои идентификаторы.
    @Test
    void refund_twoPartialRefunds_recordsBothWithDistinctIdentifiers() throws Exception {
        Transaction settled = transaction("PAID", TransactionStatus.SUCCESS);
        when(acquiringClient.refund(anyString(), any(), any()))
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
        Assertions.assertEquals(List.of(new BigDecimal("30.00"), new BigDecimal("20.00")),
                transactionRefundRepository.findByTransactionIdOrderByRefundedAtAsc(settled.getId()).stream()
                        .map(TransactionRefund::getAmount).toList(),
                "each partial refund is its own row, in the order it was made");

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
        when(acquiringClient.refund(anyString(), any(), any()))
                .thenThrow(new PaymentOutcomeUnknownException(
                        "Acquirer accepted the refund but did not confirm it: the response has no tran.match.ridByPmo"));

        mockMvc.perform(refund(settled, new BigDecimal("40.00")))
                .andExpect(status().isBadGateway());

        Transaction untouched = reload(settled);
        Assertions.assertEquals(TransactionStatus.SUCCESS, untouched.getStatus());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(untouched.getRefundedAmount()),
                "an unconfirmed refund must not be recorded locally");
        Assertions.assertTrue(refundsOf(settled).isEmpty(), "no trail for a refund that was not confirmed");
        Assertions.assertTrue(transactionRefundRepository.findByTransactionIdOrderByRefundedAtAsc(settled.getId()).isEmpty(),
                "an unconfirmed refund must not reach the dashboard either");
    }

    // То же для capture: 502, статус остаётся AUTHORIZED, снятое никуда не записывается.
    @Test
    void completeDms_providerAnswersWithoutConfirmation_returns502AndKeepsStatus() throws Exception {
        Transaction authorized = transaction("HOLD", TransactionStatus.AUTHORIZED);
        when(acquiringClient.completeDms(anyString(), any(), any()))
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
        when(acquiringClient.completeDms(anyString(), any(), any()))
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

        verify(acquiringClient, never()).refund(anyString(), any(), any());
        Transaction untouched = reload(captured);
        Assertions.assertEquals(TransactionStatus.SUCCESS, untouched.getStatus());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(untouched.getRefundedAmount()));
    }

    // Вернуть всё, что реально было списано, — это полный возврат, а не частичный.
    @Test
    void refund_afterPartialCapture_fullCapturedAmount_marksRefunded() throws Exception {
        Transaction captured = dmsTransaction("PARTIAL-FULLBACK", TransactionStatus.SUCCESS,
                AUTHORIZED_AMOUNT, CAPTURED_AMOUNT);
        when(acquiringClient.refund(anyString(), any(), any()))
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
        when(acquiringClient.refund(anyString(), any(), any()))
                .thenReturn(confirmed("REF-2"));

        // Копейка сверх авторизованной суммы всё так же отвергается — потолок ровно amount.
        mockMvc.perform(refund(settled, new BigDecimal("1500.01")))
                .andExpect(status().isBadRequest());
        verify(acquiringClient, never()).refund(anyString(), any(), any());

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
        when(acquiringClient.completeDms(anyString(), any(), any()))
                .thenThrow(new PaymentOutcomeUnknownException("Read timed out"));

        mockMvc.perform(capture(authorized, AMOUNT))
                .andExpect(status().isBadGateway());

        verify(acquiringClient, times(1)).completeDms(anyString(), any(), any());
    }

    @Test
    void refund_providerFails_isNotRetried() throws Exception {
        Transaction settled = transaction("PAID", TransactionStatus.SUCCESS);
        when(acquiringClient.refund(anyString(), any(), any()))
                .thenThrow(new PaymentOutcomeUnknownException("Read timed out"));

        mockMvc.perform(refund(settled, new BigDecimal("40.00")))
                .andExpect(status().isBadGateway());

        verify(acquiringClient, times(1)).refund(anyString(), any(), any());
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
        // @Retry на классе или интерфейсе накрыл бы все методы, списание и возврат тоже.
        Assertions.assertFalse(TxpgAcquiringClient.class.isAnnotationPresent(Retry.class),
                "@Retry on the class would retry completeDms and refund as well (P0-7)");
        Assertions.assertFalse(AcquiringClient.class.isAnnotationPresent(Retry.class),
                "@Retry on the interface would retry completeDms and refund as well (P0-7)");
    }

    // --- замок ссылки (Р-85) ----------------------------------------------------------------

    // Ловит удаление findWithLockById из lockLinkAndLoadTransaction: без замка два возврата проходят
    // потолок на одном снимке, а два списания уходят в шлюз. Замок держит отдельное соединение — как
    // чужая операция или открытие ссылки. Занятая ссылка — сразу 409, до эквайера запрос не доходит.
    @Test
    void moneyOperationsOnALockedLink_are409_andNeverReachTheAcquirer() throws Exception {
        Transaction settled = transaction("LOCKED-REFUND", TransactionStatus.SUCCESS);
        Transaction held = transaction("LOCKED-CAPTURE", TransactionStatus.AUTHORIZED);
        when(acquiringClient.refund(anyString(), any(), any())).thenReturn(confirmed("REF"));
        when(acquiringClient.completeDms(anyString(), any(), any())).thenReturn(confirmed("CAP"));

        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            lockLink(other, settled);
            lockLink(other, held);

            mockMvc.perform(refund(settled, new BigDecimal("40.00")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", is("The resource is being changed by another request, please retry")));
            mockMvc.perform(capture(held, AMOUNT))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", is("The resource is being changed by another request, please retry")));

            verify(acquiringClient, never()).refund(anyString(), any(), any());
            verify(acquiringClient, never()).completeDms(anyString(), any(), any());
            Transaction notRefunded = reload(settled);
            Assertions.assertEquals(TransactionStatus.SUCCESS, notRefunded.getStatus());
            Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(notRefunded.getRefundedAmount()));
            Transaction notCaptured = reload(held);
            Assertions.assertEquals(TransactionStatus.AUTHORIZED, notCaptured.getStatus());
            Assertions.assertNull(notCaptured.getCapturedAmount());
            other.rollback();
        }

        // Замок снят — те же запросы проходят: 409 был из-за замка, а не из-за данных.
        mockMvc.perform(refund(settled, new BigDecimal("40.00"))).andExpect(status().isOk());
        mockMvc.perform(capture(held, AMOUNT)).andExpect(status().isOk());
    }

    // --- охранные условия денежных операций -------------------------------------------------

    // Потолок считает уже возвращённое: списано 100, возвращено 60 — ещё 50 не проходит, 40 проходит и
    // закрывает операцию. Ловит сравнение суммы возврата с базой без учёта refundedAmount.
    @Test
    void refund_ceilingCountsWhatWasAlreadyRefunded() throws Exception {
        Transaction partly = dmsTransaction("PARTLY-BACK", TransactionStatus.PARTIALLY_REFUNDED, AMOUNT, AMOUNT);
        jdbcTemplate.update("UPDATE transactions SET refunded_amount = 60.00 WHERE id = ?", partly.getId());
        when(acquiringClient.refund(anyString(), any(), any())).thenReturn(confirmed("REST"));

        mockMvc.perform(refund(partly, new BigDecimal("50.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Refund amount exceeds the captured amount of the transaction")));
        verify(acquiringClient, never()).refund(anyString(), any(), any());
        Assertions.assertEquals(0, new BigDecimal("60.00").compareTo(reload(partly).getRefundedAmount()));

        mockMvc.perform(refund(partly, new BigDecimal("40.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REFUNDED")));
        Assertions.assertEquals(0, AMOUNT.compareTo(reload(partly).getRefundedAmount()));
    }

    // Возврат — только из SUCCESS и PARTIALLY_REFUNDED: холд и неоплаченный платёж ещё не деньги, у
    // неуспешного и полностью возвращённого возвращать нечего. Ловит ослабление проверки статуса.
    @Test
    void refund_fromAnUnsettledOrClosedTransaction_isRefusedBeforeTheAcquirer() throws Exception {
        for (TransactionStatus from : List.of(TransactionStatus.AUTHORIZED, TransactionStatus.PENDING,
                TransactionStatus.FAILED, TransactionStatus.REFUNDED)) {
            Transaction tx = transaction("NO-REFUND-" + from, from);
            mockMvc.perform(refund(tx, new BigDecimal("10.00")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", is("Only successful or partially refunded transactions can be refunded")));
            Assertions.assertEquals(from, statusOf(tx));
        }
        verify(acquiringClient, never()).refund(anyString(), any(), any());
        verify(acquiringClient, never()).getOrderStatus(anyString(), anyString(), any());
    }

    // Списание — только из AUTHORIZED и PENDING (SUCCESS — в completeDms_alreadyCaptured_returns400):
    // у возвращённого и неуспешного списывать нечего. Ловит ослабление проверки: ни опроса, ни клиринга.
    @Test
    void completeDms_fromARefundedOrFailedTransaction_isRefusedBeforeTheAcquirer() throws Exception {
        for (TransactionStatus from : List.of(TransactionStatus.PARTIALLY_REFUNDED, TransactionStatus.REFUNDED,
                TransactionStatus.FAILED)) {
            Transaction tx = transaction("NO-CAPTURE-" + from, from);
            mockMvc.perform(capture(tx, AMOUNT))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", is("Transaction is in status " + from
                            + ". Only PENDING or AUTHORIZED transactions can be completed.")));
            Assertions.assertEquals(from, statusOf(tx));
        }
        verify(acquiringClient, never()).completeDms(anyString(), any(), any());
        verify(acquiringClient, never()).getOrderStatus(anyString(), anyString(), any());
    }

    // После списания ссылка пересчитывает использования (P2-16): одноразовая закрывается сразу,
    // многоразовая — на последнем слоте. Ловит потерю пересчёта: оплаченная ссылка осталась бы ACTIVE.
    @Test
    void completeDms_countsTheLinkUsage_andClosesTheLinkWhenFull() throws Exception {
        when(acquiringClient.completeDms(anyString(), any(), any())).thenReturn(confirmed("CAP"));

        Transaction single = transaction("SINGLE-USE", TransactionStatus.AUTHORIZED);
        mockMvc.perform(capture(single, AMOUNT)).andExpect(status().isOk());
        Assertions.assertEquals(PaymentLinkStatus.COMPLETED, linkOf(single).getStatus());
        Assertions.assertEquals(1, linkOf(single).getCurrentPaymentsCount());

        PaymentLink multi = multiUseLink("MULTI-USE", 2);
        Transaction first = holdOn(multi, "MULTI-1");
        Transaction second = holdOn(multi, "MULTI-2");
        mockMvc.perform(capture(first, AMOUNT)).andExpect(status().isOk());
        Assertions.assertEquals(PaymentLinkStatus.ACTIVE, linkOf(first).getStatus());
        Assertions.assertEquals(1, linkOf(first).getCurrentPaymentsCount());
        mockMvc.perform(capture(second, AMOUNT)).andExpect(status().isOk());
        Assertions.assertEquals(PaymentLinkStatus.COMPLETED, linkOf(second).getStatus());
        Assertions.assertEquals(2, linkOf(second).getCurrentPaymentsCount());
    }

    // P0-9: пароль в ответе эквайера не попадает в provider_response — ни после списания, ни после
    // возврата. По контракту в ответе exec-tran пароля нет; тест сторожит withoutSecrets, если он появится.
    @Test
    void acquirerPasswordInAMoneyOperationResponse_isNotStored() throws Exception {
        Transaction held = dmsTransaction("SECRET", TransactionStatus.AUTHORIZED, AMOUNT, null);
        when(acquiringClient.completeDms(anyString(), any(), any()))
                .thenReturn(withPassword(confirmed("CAP")));
        when(acquiringClient.refund(anyString(), any(), any()))
                .thenReturn(withPassword(confirmed("REF")));

        mockMvc.perform(capture(held, AMOUNT)).andExpect(status().isOk());
        mockMvc.perform(refund(held, new BigDecimal("10.00"))).andExpect(status().isOk());

        Map<String, Object> stored = reload(held).getProviderResponse();
        Assertions.assertFalse(stored.containsKey("password"), "stored payload: " + stored);
        Assertions.assertFalse(objectMapper.writeValueAsString(stored).contains("leaked-secret"),
                "the acquirer's password must not be stored anywhere in the payload: " + stored);
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
        when(acquiringClient.completeDms(anyString(), any(), any()))
                .thenReturn(confirmed("CAP"));
        when(acquiringClient.refund(anyString(), any(), any()))
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

    // --- опрос статуса под тем же замком (Р-109) --------------------------------------

    // Списание уже у эквайера, а /status пришёл в эти секунды. Без замка опрос прочитал бы AUTHORIZED и после
    // коммита списания записал бы его обратно — с пустым capturedAmount и без mpCapture, и кнопка списания
    // вернулась бы. С замком опрос сразу получает 409 и к эквайеру не идёт, а списание сохраняется целиком.
    @Test
    void aStatusCheckDuringACapture_isRefused_andTheCaptureIsKeptWhole() throws Exception {
        Transaction held = transaction("RACE-CAPTURE", TransactionStatus.AUTHORIZED);
        CountDownLatch captureAtTheAcquirer = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(acquiringClient.completeDms(anyString(), any(), any())).thenAnswer(invocation -> {
            captureAtTheAcquirer.countDown();
            Assertions.assertTrue(release.await(10, TimeUnit.SECONDS), "the capture was not released in time");
            return confirmed("RACE");
        });
        when(acquiringClient.getOrderStatus(anyString(), anyString(), any())).thenReturn(Map.of("status", "Authorized"));

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> capture = pool.submit(() ->
                    paymentLinkService.completeDms(held.getId(), new CompleteDmsRequest(AMOUNT), head()));
            Assertions.assertTrue(captureAtTheAcquirer.await(10, TimeUnit.SECONDS), "the capture never reached the acquirer");

            mockMvc.perform(statusCheck(held))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", is("The resource is being changed by another request, please retry")));

            release.countDown();
            capture.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }

        verify(acquiringClient, never()).getOrderStatus(anyString(), anyString(), any());
        Transaction captured = reload(held);
        Assertions.assertEquals(TransactionStatus.SUCCESS, captured.getStatus());
        Assertions.assertEquals(0, AMOUNT.compareTo(captured.getCapturedAmount()));
        Assertions.assertNotNull(captured.getProviderResponse().get("mpCapture"));
    }

    // На запертой ссылке ни один опрос к эквайеру не идёт: /status — 409, страница возврата плательщика рисует
    // последнее известное состояние, сверка отдаёт строку следующему проходу.
    @Test
    void everyStatusRefreshOnALockedLink_leavesTheAcquirerAlone() throws Exception {
        Transaction pending = transaction("LOCKED-REFRESH", TransactionStatus.PENDING);
        when(acquiringClient.getOrderStatus(anyString(), anyString(), any())).thenReturn(Map.of("status", "FullyPaid"));

        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            lockLink(other, pending);

            mockMvc.perform(statusCheck(pending)).andExpect(status().isConflict());
            mockMvc.perform(get("/api/v1/payment-links/redirect/{tx}", pending.getRidByMerchant()))
                    .andExpect(status().isOk());
            Assertions.assertThrows(PessimisticLockingFailureException.class,
                    () -> paymentLinkService.reconcileOne(pending.getId(), Duration.ofHours(24)));

            verify(acquiringClient, never()).getOrderStatus(anyString(), anyString(), any());
            Assertions.assertEquals(TransactionStatus.PENDING, statusOf(pending));
            other.rollback();
        }

        // Замок снят — тот же опрос доходит до эквайера: 409 был из-за замка, а не из-за данных.
        mockMvc.perform(statusCheck(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")));
    }

    private JsonNode historyOf(Transaction tx) throws Exception {
        String body = mockMvc.perform(get("/api/v1/transactions/{id}", tx.getId())
                        .header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("statusHistory");
    }

    private MockHttpServletRequestBuilder statusCheck(Transaction tx) {
        return get("/api/v1/transactions/{id}/status", tx.getId()).header(HttpHeaders.AUTHORIZATION, headToken);
    }

    // Тот же руководитель, что в headToken, — для вызова сервиса мимо MockMvc.
    private static UserPrincipal head() {
        return new UserPrincipal("head-user", "head-user@test.com", "COMPANY_HEAD", "test-company");
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

    // Держит строку ссылки под FOR UPDATE в чужой транзакции, пока соединение не откатят.
    private void lockLink(Connection connection, Transaction tx) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement(
                "SELECT id FROM payment_links WHERE id = ? FOR UPDATE")) {
            lock.setObject(1, tx.getLink().getId());
            lock.executeQuery().close();
        }
    }

    private PaymentLink multiUseLink(String key, int maxPayments) {
        return paymentLinkRepository.save(PaymentLink.builder()
                .providerReference("RID-" + key)
                .merchantOrderId(key)
                .terminalId(TERMINAL_ID)
                .amount(AMOUNT)
                .currency("AZN")
                .description("Fixture for " + key)
                .paymentType(PaymentType.DMS)
                .usageType(UsageType.MULTIPLE)
                .maxPayments(maxPayments)
                .currentPaymentsCount(0)
                .status(PaymentLinkStatus.ACTIVE)
                .build());
    }

    private Transaction holdOn(PaymentLink link, String key) {
        return transactionRepository.save(Transaction.builder()
                .link(link)
                .ridByMerchant(UUID.randomUUID())
                .providerOrderId("ORD-" + key)
                .providerPassword("provider-password")
                .amount(AMOUNT)
                .refundedAmount(BigDecimal.ZERO)
                .status(TransactionStatus.AUTHORIZED)
                .build());
    }

    private static MoneyOperationResult withPassword(MoneyOperationResult result) {
        Map<String, Object> raw = new HashMap<>(result.raw());
        raw.put("password", "leaked-secret");
        return new MoneyOperationResult(result.approvalCode(), result.tranActionId(), result.ridByPmo(), raw);
    }

    private PaymentLink linkOf(Transaction tx) {
        return paymentLinkRepository.findById(tx.getLink().getId()).orElseThrow();
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
