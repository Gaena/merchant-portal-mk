package az.millikart.pbl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// lastPaidAt — когда ссылку оплатили в последний раз (P2-15). refund переводит саму строку
// транзакции в REFUNDED и второй строки не пишет, поэтому поиск только по SUCCESS показал бы
// возвращённую ссылку никогда не оплаченной. Важнее второе: страница списка разрешается одним
// запросом — построчный поиск пишется сам собой и на странице из одной строки выглядит здоровым.
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
public class PaymentLinkLastPaidAtTest {

    private static final int TERMINAL_ID = 123456789;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private PaymentLinkRepository paymentLinkRepository;

    @Autowired
    private TerminalRepository terminalRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // Шпион, а не мок: настоящие запросы выполняются, считаем лишь кого и сколько раз спросили.
    @SpyBean
    private TransactionRepository transactionRepository;

    private String adminToken;

    @BeforeEach
    public void setup() {
        Mockito.reset(transactionRepository);
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

        adminToken = "Bearer " + jwtProvider.generateToken("admin", "admin@test.com", "SYSTEM_ADMIN", null);
    }

    // Фикстуры

    private PaymentLink seedLink(String orderId) {
        return paymentLinkRepository.saveAndFlush(PaymentLink.builder()
                .merchantOrderId(orderId)
                .providerReference("REF-" + orderId)
                .terminalId(TERMINAL_ID)
                .amount(new BigDecimal("100.00"))
                .currency("AZN")
                .description("Link " + orderId)
                .paymentType(PaymentType.SMS)
                .usageType(UsageType.MULTIPLE)
                .maxPayments(5)
                .currentPaymentsCount(0)
                .status(PaymentLinkStatus.ACTIVE)
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build());
    }

    // Пауза держит created_at строго возрастающим на грубых часах, чтобы «самый новый из трёх» был
    // однозначен: колонку заполняет CreationTimestamp, и тесты не должны разрешать ничью порядком
    // вставки.
    private Transaction seedTransaction(PaymentLink link, TransactionStatus status) {
        Transaction saved = transactionRepository.saveAndFlush(Transaction.builder()
                .link(link)
                .ridByMerchant(UUID.randomUUID())
                .providerOrderId("ORD-" + UUID.randomUUID())
                .providerPassword("secret")
                .amount(new BigDecimal("100.00"))
                .refundedAmount(BigDecimal.ZERO)
                .status(status)
                .build());
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return saved;
    }

    private JsonNode getLink(UUID id) throws Exception {
        return body(mockMvc.perform(get("/api/v1/payment-links/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()));
    }

    private JsonNode body(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    // null в JSON и отсутствие поля равно значат «не оплачивалась» — обрабатываем одинаково.
    private static String lastPaidAt(JsonNode node) {
        JsonNode value = node.get("lastPaidAt");
        return value == null || value.isNull() ? null : value.asText();
    }

    // 1, 2. Значение: один платёж, затем самый новый из трёх

    @Test
    public void oneSuccessfulPayment_isTheLastPaidAt() throws Exception {
        PaymentLink link = seedLink("ORDER-1");
        Transaction paid = seedTransaction(link, TransactionStatus.SUCCESS);

        Assertions.assertEquals(paid.getCreatedAt(), Instant.parse(lastPaidAt(getLink(link.getId()))),
                "lastPaidAt must be the moment of that payment");
    }

    @Test
    public void threePayments_reportTheNewest() throws Exception {
        PaymentLink link = seedLink("ORDER-2");
        seedTransaction(link, TransactionStatus.SUCCESS);
        seedTransaction(link, TransactionStatus.SUCCESS);
        Transaction newest = seedTransaction(link, TransactionStatus.SUCCESS);

        Assertions.assertEquals(newest.getCreatedAt(), Instant.parse(lastPaidAt(getLink(link.getId()))),
                "a multi-use link reports its most recent payment (Р-46)");
    }

    // 3, 4. Возврат не отменяет платёж

    // Ради этого PAID_STATUSES — набор, а не сравнение с SUCCESS: возврат переписывает статус
    // самой строки платежа, и единственный след того, что деньги переходили, — эта же строка
    // в статусе REFUNDED.
    @Test
    public void refundedPayment_keepsItsPaymentDate() throws Exception {
        PaymentLink link = seedLink("ORDER-3");
        Transaction paid = seedTransaction(link, TransactionStatus.REFUNDED);

        Assertions.assertEquals(paid.getCreatedAt(), Instant.parse(lastPaidAt(getLink(link.getId()))),
                "a refunded link was paid: the payment date must survive the refund");
    }

    @Test
    public void partiallyRefundedPayment_keepsItsPaymentDate() throws Exception {
        PaymentLink link = seedLink("ORDER-4");
        Transaction paid = seedTransaction(link, TransactionStatus.PARTIALLY_REFUNDED);

        Assertions.assertEquals(paid.getCreatedAt(), Instant.parse(lastPaidAt(getLink(link.getId()))),
                "a partial refund leaves the payment date untouched");
    }

    // 5, 6. Ничего не оплачивали

    @Test
    public void onlyFailedAndPendingAttempts_leaveLastPaidAtEmpty() throws Exception {
        PaymentLink link = seedLink("ORDER-5");
        seedTransaction(link, TransactionStatus.FAILED);
        seedTransaction(link, TransactionStatus.PENDING);

        Assertions.assertNull(lastPaidAt(getLink(link.getId())),
                "an attempt that never settled is not a payment");
    }

    // DMS-холд — деньги зарезервированы, а не взяты: платежом он становится только при capture.
    @Test
    public void authorizedHold_isNotAPayment() throws Exception {
        PaymentLink link = seedLink("ORDER-5b");
        seedTransaction(link, TransactionStatus.AUTHORIZED);

        Assertions.assertNull(lastPaidAt(getLink(link.getId())),
                "AUTHORIZED is a hold, not a settled payment");
    }

    @Test
    public void linkWithoutTransactions_hasNoLastPaidAt() throws Exception {
        PaymentLink link = seedLink("ORDER-6");

        Assertions.assertNull(lastPaidAt(getLink(link.getId())),
                "a link nobody paid has no payment date");
    }

    // 7. Главный тест: один запрос на страницу, а не по запросу на строку

    // Двадцать ссылок, двадцать платежей, одна страница — и ровно один групповой запрос за всей
    // страницей. Три проверки ловят три разные ошибки: батчевый метод вызван один раз, построчный
    // не вызван вовсе, а число подготовленных Hibernate запросов не растёт с числом строк.
    @Test
    public void listOfTwentyLinks_resolvesDatesWithASingleQuery() throws Exception {
        for (int i = 0; i < 20; i++) {
            seedTransaction(seedLink("BULK-" + i), TransactionStatus.SUCCESS);
        }
        Mockito.clearInvocations(transactionRepository);

        Statistics statistics = statistics();
        statistics.clear();

        JsonNode page = body(mockMvc.perform(get("/api/v1/payment-links")
                        .param("page", "0").param("size", "20")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", org.hamcrest.Matchers.is(20))));

        long statementsForTwenty = statistics.getPrepareStatementCount();

        verify(transactionRepository, times(1)).findLastPaidAtByLinkIds(any(), any());
        verify(transactionRepository, never()).findFirstByLinkIdAndStatusInOrderByCreatedAtDesc(any(), any());

        // Каждая строка действительно получила дату: один запрос, не разрешивший ничего, прошёл бы
        // проверки счётчиков выше и упал бы здесь.
        for (JsonNode row : page.get("content")) {
            Assertions.assertNotNull(lastPaidAt(row),
                    "every paid link on the page must carry its date: " + row);
        }

        // Страница из одной строки для сравнения: при построчной выборке дат двадцать строк стоили
        // бы на девятнадцать запросов больше.
        statistics.clear();
        mockMvc.perform(get("/api/v1/payment-links")
                        .param("page", "0").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());
        long statementsForOne = statistics.getPrepareStatementCount();

        Assertions.assertEquals(statementsForOne, statementsForTwenty,
                "the number of queries behind a page must not depend on how many rows it has "
                        + "(one row: " + statementsForOne + ", twenty rows: " + statementsForTwenty + ")");
    }

    // 8. Пустая страница ничего не спрашивает

    // IN () — невалидный SQL: пустая страница обязана пропустить запрос, а не строить его.
    @Test
    public void emptyPage_doesNotQueryForDatesAtAll() throws Exception {
        Mockito.clearInvocations(transactionRepository);

        mockMvc.perform(get("/api/v1/payment-links")
                        .param("page", "0").param("size", "20")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", org.hamcrest.Matchers.is(0)));

        verify(transactionRepository, never()).findLastPaidAtByLinkIds(any(), any());
    }

    // 9. Одиночный ответ и строка списка сходятся

    @Test
    public void singleResponseAndListRow_carryTheSameValue() throws Exception {
        PaymentLink link = seedLink("ORDER-9");
        seedTransaction(link, TransactionStatus.SUCCESS);
        Transaction newest = seedTransaction(link, TransactionStatus.SUCCESS);

        String fromSingle = lastPaidAt(getLink(link.getId()));

        JsonNode page = body(mockMvc.perform(get("/api/v1/payment-links")
                        .param("page", "0").param("size", "20")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()));
        JsonNode row = page.get("content").get(0);

        Assertions.assertNotNull(fromSingle, "the single response must carry the date");
        Assertions.assertEquals(fromSingle, lastPaidAt(row),
                "one link, two endpoints, one answer");
        Assertions.assertEquals(newest.getCreatedAt(), Instant.parse(fromSingle));
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
