package az.millikart.pbl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.common.audit.AuditLog;
import az.millikart.common.audit.AuditOutcome;
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
import az.millikart.pbl.provider.dto.MoneyOperationResult;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

// P2-14: pbl пишет в журнал аудита — ссылки и, прежде всего, деньги. Раньше сервис, двигающий
// деньги, аудита не имел вовсе: возврат оставлял строку в transactions и ничего о том, кто его
// заказал. Самый важный случай — эквайер не подтвердил: локально ничего не коммитится, мерчант
// получает 502, и без синхронной записи единственным следом попытки была бы строка лога.
@SpringBootTest
@AutoConfigureMockMvc
class PblAuditIntegrationTest {

    private static final int TERMINAL_ID = 830001;
    private static final int FOREIGN_TERMINAL_ID = 830002;
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

    @Autowired
    private AuditLogTestRepository auditLogs;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AcquiringClient acquiringClient;

    private String headToken;

    @BeforeEach
    void setUp() {
        auditLogs.deleteAll();
        transactionRepository.deleteAll();
        paymentLinkRepository.deleteAll();
        terminalRepository.deleteAll();

        terminalRepository.save(terminal(TERMINAL_ID, "test-company"));
        terminalRepository.save(terminal(FOREIGN_TERMINAL_ID, "other-company"));

        headToken = "Bearer " + jwtProvider.generateToken(
                "head-user", "head-user@test.com", "COMPANY_HEAD", "test-company");
    }

    // 13-14. Деньги, которые двигались

    @Test
    void refund_isRecordedWithAmountAndAcquirerIdentifiers() throws Exception {
        Transaction paid = transaction(TransactionStatus.SUCCESS, PaymentType.SMS);
        when(acquiringClient.refund(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new MoneyOperationResult("TRAN-77", "RID-42", "APPR-9", Map.of("status", "ok")));

        mockMvc.perform(post("/api/v1/transactions/" + paid.getId() + "/refund")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":40.00}"))
                .andExpect(status().isOk());

        AuditLog record = single("REFUND");
        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(record.getEntityType()).isEqualTo("TRANSACTION");
        assertThat(record.getEntityId()).isEqualTo(paid.getId().toString());
        assertThat(record.getPerformedBy()).isEqualTo("head-user@test.com");
        assertThat(record.getDetails())
                .contains("40.00", "AZN", "RID-42", "TRAN-77", "PARTIALLY_REFUNDED");
    }

    @Test
    void capture_isRecordedWithAmountAndAcquirerIdentifiers() throws Exception {
        Transaction held = transaction(TransactionStatus.AUTHORIZED, PaymentType.DMS);
        when(acquiringClient.completeDms(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new MoneyOperationResult("TRAN-88", "RID-43", "APPR-8", Map.of("status", "ok")));

        mockMvc.perform(post("/api/v1/transactions/" + held.getId() + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isOk());

        AuditLog record = single("CAPTURE");
        assertThat(record.getDetails()).contains("100.00", "AZN", "RID-43", "TRAN-88");
    }

    // 15. Запись, которая обязана существовать именно потому, что ничего не закоммитилось

    // Главный тест задачи: эквайер не подтвердил, сервис отвечает 502 и откатывает транзакцию —
    // возврат ни применён, ни отклонён, разбирать придётся руками. AFTER_COMMIT здесь не сработал
    // бы (коммита нет), поэтому запись синхронная и остаётся единственным следом в базе, что
    // операцию вообще пытались провести.
    @Test
    void refundWithUnknownOutcome_isRecordedEvenThoughTheTransactionRolledBack() throws Exception {
        Transaction paid = transaction(TransactionStatus.SUCCESS, PaymentType.SMS);
        when(acquiringClient.refund(any(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new PaymentOutcomeUnknownException("No confirmation received from the acquirer"));

        mockMvc.perform(post("/api/v1/transactions/" + paid.getId() + "/refund")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":40.00}"))
                .andExpect(status().isBadGateway());

        // Локально ничего не применилось...
        Transaction unchanged = transactionRepository.findById(paid.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(unchanged.getRefundedAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // ...и журнал — единственное место, где сказано, что это было. UNRESOLVED, а не SUCCESS
        // (P3-2): запись, которую придётся разбирать руками, не имеет права выглядеть на экране
        // чистой операцией.
        AuditLog record = single("REFUND");
        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.UNRESOLVED);
        assertThat(record.getDetails())
                .contains("40.00", "unconfirmed", "reconcile")
                .contains(paid.getProviderOrderId());
    }

    @Test
    void captureWithUnknownOutcome_isRecordedEvenThoughTheTransactionRolledBack() throws Exception {
        Transaction held = transaction(TransactionStatus.AUTHORIZED, PaymentType.DMS);
        when(acquiringClient.completeDms(any(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new PaymentOutcomeUnknownException("No confirmation received from the acquirer"));

        mockMvc.perform(post("/api/v1/transactions/" + held.getId() + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isBadGateway());

        assertThat(transactionRepository.findById(held.getId()).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.AUTHORIZED);
        AuditLog record = single("CAPTURE");
        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.UNRESOLVED);
        assertThat(record.getDetails()).contains("unconfirmed", "reconcile");
    }

    // 16. Отказы

    @Test
    void accessToAnotherCompanysTransaction_isRecordedAsDenied() throws Exception {
        Transaction foreign = transaction(TransactionStatus.SUCCESS, PaymentType.SMS, FOREIGN_TERMINAL_ID);

        mockMvc.perform(post("/api/v1/transactions/" + foreign.getId() + "/refund")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00}"))
                .andExpect(status().isForbidden());

        // READ — то же действие, которым каталог пишет свои отказы по терминалам, а не приватное
        // имя этого сервиса (P3-2): один поиск обязан находить отказ, где бы его ни записали.
        AuditLog record = single("READ");
        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(record.getEntityType()).isEqualTo("TERMINAL");
        assertThat(record.getEntityId()).isEqualTo(String.valueOf(FOREIGN_TERMINAL_ID));
        assertThat(record.getPerformedBy()).isEqualTo("head-user@test.com");
        assertThat(record.getCompanyId())
                .as("filed under the actor's company, never the one they reached for")
                .isEqualTo("test-company");
    }

    // 17. Ссылки

    @Test
    void creatingAndCancellingLink_areRecorded() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "merchantOrderId", "order-audit-1",
                "terminal", TERMINAL_ID,
                "amount", AMOUNT,
                "currency", "AZN",
                "description", "Audited link",
                "paymentType", "SMS",
                "usageType", "SINGLE"));

        String created = mockMvc.perform(post("/api/v1/payment-links")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String linkId = objectMapper.readTree(created).get("id").asText();

        AuditLog createRecord = single("CREATE");
        assertThat(createRecord.getEntityType()).isEqualTo("PAYMENT_LINK");
        assertThat(createRecord.getEntityId()).isEqualTo(linkId);
        assertThat(createRecord.getDetails()).contains("100.00", "AZN", String.valueOf(TERMINAL_ID));

        mockMvc.perform(patch("/api/v1/payment-links/" + linkId)
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELED\"}"))
                .andExpect(status().isOk());

        assertThat(single("CANCEL").getDetails()).contains("ACTIVE -> CANCELED");
    }

    // 18. Сломанный журнал не должен стоить мерчанту возврата

    @Test
    void auditFailure_doesNotBreakRefund() throws Exception {
        Transaction paid = transaction(TransactionStatus.SUCCESS, PaymentType.SMS);
        when(acquiringClient.refund(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new MoneyOperationResult("TRAN-99", "RID-99", "APPR-99", Map.of("status", "ok")));

        jdbcTemplate.execute("DROP TABLE audit_logs");
        try {
            mockMvc.perform(post("/api/v1/transactions/" + paid.getId() + "/refund")
                            .header(HttpHeaders.AUTHORIZATION, headToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":100.00}"))
                    .andExpect(status().isOk());

            assertThat(transactionRepository.findById(paid.getId()).orElseThrow().getStatus())
                    .as("the money moved; only the journal was broken")
                    .isEqualTo(TransactionStatus.REFUNDED);
        } finally {
            restoreAuditTable();
        }
    }

    // Фикстуры

    private static Terminal terminal(int id, String companyId) {
        return Terminal.builder()
                .id(id)
                .name("Terminal " + id)
                .login("TerminalSys/Admin")
                .password("1234")
                .companyId(companyId)
                .build();
    }

    private Transaction transaction(TransactionStatus status, PaymentType paymentType) {
        return transaction(status, paymentType, TERMINAL_ID);
    }

    private Transaction transaction(TransactionStatus status, PaymentType paymentType, int terminalId) {
        String key = UUID.randomUUID().toString().substring(0, 8);
        PaymentLink link = paymentLinkRepository.save(PaymentLink.builder()
                .providerReference("RID-" + key)
                .merchantOrderId("order-" + key)
                .terminalId(terminalId)
                .amount(AMOUNT)
                .currency("AZN")
                .description("Fixture " + key)
                .paymentType(paymentType)
                .usageType(UsageType.SINGLE)
                .currentPaymentsCount(0)
                .status(PaymentLinkStatus.ACTIVE)
                .build());

        return transactionRepository.save(Transaction.builder()
                .link(link)
                .merchantRid(UUID.randomUUID())
                .providerOrderId("ORD-" + key)
                .providerPassword("provider-password")
                .amount(AMOUNT)
                .refundedAmount(BigDecimal.ZERO)
                .status(status)
                .build());
    }

    private AuditLog single(String action) {
        List<AuditLog> records = auditLogs.findAll().stream()
                .filter(record -> action.equals(record.getAction()))
                .toList();
        assertThat(records).as("expected exactly one %s record, got %s", action, records.size()).hasSize(1);
        return records.get(0);
    }

    private void restoreAuditTable() {
        jdbcTemplate.execute("""
                CREATE TABLE audit_logs (
                    id uuid NOT NULL,
                    entity_type varchar(50) NOT NULL,
                    entity_id varchar(255) NOT NULL,
                    action varchar(50) NOT NULL,
                    performed_by varchar(255) NOT NULL,
                    company_id varchar(255),
                    details varchar(4000),
                    client_ip varchar(45),
                    outcome varchar(16) DEFAULT 'SUCCESS' NOT NULL,
                    created_at timestamp,
                    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
                )""");
    }
}
