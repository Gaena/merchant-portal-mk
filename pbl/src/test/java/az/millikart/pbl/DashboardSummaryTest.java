package az.millikart.pbl;

import az.millikart.common.testing.PostgresTestContainer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import az.millikart.pbl.provider.StubAcquirerConfig;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

// Сводка главной страницы (P3-7). До неё главная считала аналитику в браузере по двадцати строкам
// и называла результат «All system transactions»; здесь проверяется, что теперь считает база и
// что она считает именно то, что написано на экране.
//
// На настоящей PostgreSQL, а не на H2, и здесь это самое существенное из всех: сводка считается
// группировками с границами суток в часовом поясе Баку. Функции работы с датами и раскладка по
// окнам у двух СУБД разные, и на эмуляции тест подтверждал бы чужую арифметику.
@SpringBootTest
@AutoConfigureMockMvc
@Import({StubAcquirerConfig.class, PostgresTestContainer.class})
public class DashboardSummaryTest {

    private static final int TERMINAL_A = 1001;
    private static final int TERMINAL_B = 2002;
    private static final String COMPANY_A = "company-a";
    private static final String COMPANY_B = "company-b";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TerminalRepository terminalRepository;
    @Autowired private PaymentLinkRepository paymentLinkRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AcquiringClient acquiringClient;

    private ZoneId reportZone;
    private String adminToken;
    private String headAToken;
    private String headWithoutCompanyToken;
    private String unknownRoleToken;

    @BeforeEach
    public void setup() {
        Mockito.reset(acquiringClient);
        transactionRepository.deleteAll();
        paymentLinkRepository.deleteAll();
        terminalRepository.deleteAll();

        terminalRepository.save(terminal(TERMINAL_A, "Main e-commerce", COMPANY_A));
        terminalRepository.save(terminal(TERMINAL_B, "Other company terminal", COMPANY_B));

        // Пояс отчёта берётся из той же настройки, что и у сервиса: тест не должен утверждать,
        // что он бакинский, — он должен утверждать, что сутки режутся в поясе отчёта.
        reportZone = ZoneId.of("Asia/Baku");

        adminToken = token("admin", "SYSTEM_ADMIN", null);
        headAToken = token("head-a", "COMPANY_HEAD", COMPANY_A);
        headWithoutCompanyToken = token("head-none", "COMPANY_HEAD", null);
        unknownRoleToken = token("weird", "MERCHANT_ADMIN", COMPANY_A);
    }

    // ─── доступ ──────────────────────────────────────────────────────────────

    @Test
    public void companyHead_seesOnlyOwnCompanyMoney() throws Exception {
        paid(link(TERMINAL_A, "AZN"), "100.00");
        paid(link(TERMINAL_B, "AZN"), "999.00");

        JsonNode own = totals(summary(headAToken, ""), "AZN");
        Assertions.assertEquals(new BigDecimal("100.00"), amount(own, "netAmount"),
                "a company head must not see another company's money");
        Assertions.assertEquals(1, own.get("paidCount").asLong());
    }

    @Test
    public void systemAdmin_seesEveryCompany() throws Exception {
        paid(link(TERMINAL_A, "AZN"), "100.00");
        paid(link(TERMINAL_B, "AZN"), "999.00");

        Assertions.assertEquals(new BigDecimal("1099.00"),
                amount(totals(summary(adminToken, ""), "AZN"), "netAmount"),
                "SYSTEM_ADMIN is a global reader, as in listTransactions");
    }

    // Пустая сводка, а не 403: у пользователя без компании нет своих денег, но и запрещать ему
    // смотреть не на что. Так же ведёт себя список транзакций.
    @Test
    public void companyHeadWithoutCompany_getsZeros_notForbidden() throws Exception {
        paid(link(TERMINAL_A, "AZN"), "100.00");

        JsonNode body = summary(headWithoutCompanyToken, "");
        Assertions.assertEquals(0, body.get("totals").size(), "no company means no money, not an error");
        Assertions.assertEquals(6, body.get("statusBreakdown").size(), "the shape stays the same");
        Assertions.assertEquals(24, body.get("hourlyTotals").size());
    }

    @Test
    public void unrecognisedRole_isRefused() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, unknownRoleToken))
                .andExpect(status().isForbidden());
    }

    // ─── деньги ──────────────────────────────────────────────────────────────

    // amount остаётся авторизованной суммой, captured_amount — тем, что реально списали.
    @Test
    public void partialCapture_countsWhatWasCaptured_notWhatWasAuthorised() throws Exception {
        seed(link(TERMINAL_A, "AZN"), TransactionStatus.SUCCESS, "100.00", "60.00", "0.00", Instant.now());

        JsonNode azn = totals(summary(headAToken, ""), "AZN");
        Assertions.assertEquals(new BigDecimal("60.00"), amount(azn, "paidAmount"),
                "a partially captured payment brought 60, not the 100 it was authorised for");
        Assertions.assertEquals(new BigDecimal("60.00"), amount(azn, "netAmount"));
    }

    // Возврат не отменяет факт оплаты (P2-16): платёж остаётся оплаченным и уменьшается на возврат,
    // а не выпадает из выручки целиком, как считала прежняя главная.
    @Test
    public void refundedPayment_staysPaid_andOnlyReducesNet() throws Exception {
        seed(link(TERMINAL_A, "AZN"), TransactionStatus.PARTIALLY_REFUNDED, "100.00", "100.00", "30.00", Instant.now());

        JsonNode azn = totals(summary(headAToken, ""), "AZN");
        Assertions.assertEquals(1, azn.get("paidCount").asLong(), "the money did arrive");
        Assertions.assertEquals(1, azn.get("refundedCount").asLong());
        Assertions.assertEquals(new BigDecimal("100.00"), amount(azn, "paidAmount"));
        Assertions.assertEquals(new BigDecimal("30.00"), amount(azn, "refundedAmount"));
        Assertions.assertEquals(new BigDecimal("70.00"), amount(azn, "netAmount"));
    }

    // Колонки currency у transactions нет вовсе — она на payment_links, и туда попадает любой
    // трёхбуквенный код. Сводный итог поверх двух валют был бы числом, которого не существует.
    @Test
    public void twoCurrencies_areNeverMerged() throws Exception {
        paid(link(TERMINAL_A, "AZN"), "100.00");
        paid(link(TERMINAL_A, "USD"), "50.00");

        JsonNode body = summary(headAToken, "");
        Assertions.assertEquals(2, body.get("totals").size(), "one row per currency, no grand total");
        Assertions.assertEquals(new BigDecimal("100.00"), amount(totals(body, "AZN"), "netAmount"));
        Assertions.assertEquals(new BigDecimal("50.00"), amount(totals(body, "USD"), "netAmount"));
    }

    // ─── окно и пояс ─────────────────────────────────────────────────────────

    @Test
    public void fromAfterTo_isRefused() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .param("from", "2026-08-24T00:00:00Z")
                        .param("to", "2026-08-20T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, headAToken))
                .andExpect(status().isBadRequest());
    }

    // Отказ, а не зажим: молча отдать окно, которого не просили, — снова показать цифру не за тот
    // период и назвать её настоящей.
    @Test
    public void windowBeyondTheCap_isRefused() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-08-24T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, headAToken))
                .andExpect(status().isBadRequest());
    }

    // Дыра в графике читается как «в этот день не работали», пропуск дня — как «данных нет».
    // Это разные утверждения, и второе — неправда.
    @Test
    public void dayWithoutPayments_isPresentWithZeros() throws Exception {
        paid(link(TERMINAL_A, "AZN"), "100.00");

        JsonNode daily = summary(headAToken, "").get("dailyTotals");
        Assertions.assertEquals(7, daily.size(), "seven whole days by default, gaps included");
        long empty = 0;
        for (JsonNode day : daily) {
            if (day.get("transactionCount").asLong() == 0) {
                empty++;
                Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(new BigDecimal(day.get("netAmount").asText())),
                        "a day without payments is zero, not missing");
            }
        }
        Assertions.assertEquals(6, empty, "only today has a payment");
    }

    // Сторож пояса. Два платежа по разные стороны полуночи пояса отчёта обязаны попасть
    // в разные сутки. Одного платежа для этого мало: сутки корзины сервис берёт из её самого
    // раннего момента, и одиночная строка легла бы в правильную дату при любом дефекте
    // группировки — разъезжаются именно **соседние** корзины.
    @Test
    public void paymentsStraddlingMidnight_landOnTheirOwnDays() throws Exception {
        // Позавчера и вчера, а не вчера и сегодня: обе даты целиком в прошлом, поэтому тест
        // не зависит от того, в какой час суток его запустили.
        LocalDate before = LocalDate.now(reportZone).minusDays(2);
        LocalDate after = LocalDate.now(reportZone).minusDays(1);
        PaymentLink link = link(TERMINAL_A, "AZN");
        seed(link, TransactionStatus.SUCCESS, "100.00", null, "0.00",
                before.atTime(LocalTime.of(23, 30)).atZone(reportZone).toInstant());
        seed(link, TransactionStatus.SUCCESS, "100.00", null, "0.00",
                after.atTime(LocalTime.of(0, 30)).atZone(reportZone).toInstant());

        JsonNode daily = summary(headAToken, "").get("dailyTotals");
        for (JsonNode day : daily) {
            LocalDate date = LocalDate.parse(day.get("date").asText());
            long expected = date.equals(before) || date.equals(after) ? 1 : 0;
            Assertions.assertEquals(expected, day.get("transactionCount").asLong(),
                    "23:30 and 00:30 in the report zone are two different days, " + date);
        }
    }

    @Test
    public void allSixStatuses_arePresentEvenWhenZero() throws Exception {
        paid(link(TERMINAL_A, "AZN"), "100.00");

        JsonNode breakdown = summary(headAToken, "").get("statusBreakdown");
        Assertions.assertEquals(TransactionStatus.values().length, breakdown.size());
        for (TransactionStatus status : TransactionStatus.values()) {
            Assertions.assertTrue(breakdown.findValuesAsText("status").contains(status.name()),
                    status + " must have a slice, even an empty one");
        }
    }

    // Топ терминалов показывает логин и имя из таблицы, а не «TRM-<id>» и не «Default Terminal».
    // Логин здесь потому, что по нему мерчант терминал и опознаёт.
    @Test
    public void topTerminals_carryTheirRealLoginAndName() throws Exception {
        paid(link(TERMINAL_A, "AZN"), "100.00");

        JsonNode top = summary(headAToken, "").get("topTerminals");
        Assertions.assertEquals(1, top.size());
        Assertions.assertEquals(TERMINAL_A, top.get(0).get("terminalId").asInt());
        Assertions.assertEquals("Main e-commerce", top.get(0).get("terminalName").asText());
        Assertions.assertEquals("login-" + TERMINAL_A, top.get(0).get("terminalLogin").asText());
    }

    // ─── чтение транзакции по id ─────────────────────────────────────────────

    @Test
    public void transaction_isReadableById_byItsOwner() throws Exception {
        Transaction tx = paid(link(TERMINAL_A, "AZN"), "100.00");

        JsonNode body = exact(mockMvc.perform(
                        get("/api/v1/transactions/{id}", tx.getId())
                                .header(HttpHeaders.AUTHORIZATION, headAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        Assertions.assertEquals(tx.getId().toString(), body.get("id").asText());
        Assertions.assertEquals("AZN", body.get("currency").asText());
        // Открытие карточки не стоит внешнего вызова: за свежим исходом ходит /{id}/status.
        Mockito.verifyNoInteractions(acquiringClient);
    }

    @Test
    public void foreignTransaction_isNotReadableById() throws Exception {
        Transaction foreign = paid(link(TERMINAL_B, "AZN"), "100.00");

        mockMvc.perform(get("/api/v1/transactions/{id}", foreign.getId())
                        .header(HttpHeaders.AUTHORIZATION, headAToken))
                .andExpect(status().isForbidden());
    }

    // «Последние операции» на главной берут первую страницу списка. До P3-7 порядок у списка
    // не был задан вовсе — база отдавала строки как ей удобно, и «последние» были просто какими-то.
    @Test
    public void transactionList_isOrderedNewestFirst() throws Exception {
        PaymentLink link = link(TERMINAL_A, "AZN");
        Instant now = Instant.now();
        seed(link, TransactionStatus.SUCCESS, "10.00", null, "0.00", now.minus(3, ChronoUnit.HOURS));
        Transaction newest = seed(link, TransactionStatus.SUCCESS, "20.00", null, "0.00", now);
        seed(link, TransactionStatus.SUCCESS, "30.00", null, "0.00", now.minus(1, ChronoUnit.HOURS));

        JsonNode content = exact(mockMvc.perform(get("/api/v1/transactions")
                        .param("page", "0").param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, headAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("content");

        Assertions.assertEquals(3, content.size());
        Assertions.assertEquals(newest.getId().toString(), content.get(0).get("id").asText(),
                "the newest payment comes first, or 'recent transactions' means nothing");
    }

    // ─── фикстуры ────────────────────────────────────────────────────────────

    private String token(String userId, String role, String companyId) {
        return "Bearer " + jwtProvider.generateToken(userId, userId + "@test.com", role, companyId);
    }

    private Terminal terminal(int id, String name, String companyId) {
        return Terminal.builder().id(id).name(name).login("login-" + id).password("secret")
                .companyId(companyId).build();
    }

    private PaymentLink link(int terminalId, String currency) {
        return paymentLinkRepository.saveAndFlush(PaymentLink.builder()
                .merchantOrderId("ORDER-" + UUID.randomUUID())
                .providerReference("REF-" + UUID.randomUUID())
                .terminalId(terminalId)
                .amount(new BigDecimal("100.00"))
                .currency(currency)
                .description("Link")
                .paymentType(PaymentType.SMS)
                .usageType(UsageType.SINGLE)
                .maxPayments(1)
                .currentPaymentsCount(0)
                .status(PaymentLinkStatus.ACTIVE)
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build());
    }

    private Transaction paid(PaymentLink link, String amount) {
        return seed(link, TransactionStatus.SUCCESS, amount, null, "0.00", Instant.now());
    }

    // created_at заполняет @CreationTimestamp, задать его вставкой нельзя — сдвигаем колонку
    // относительным UPDATE, как это делает тест фоновой сверки. Относительный сдвиг не зависит
    // от того, в каком поясе Hibernate положил значение.
    private Transaction seed(PaymentLink link, TransactionStatus status,
                             String amount, String captured, String refunded, Instant at) {
        Transaction saved = transactionRepository.saveAndFlush(Transaction.builder()
                .link(link)
                .merchantRid(UUID.randomUUID())
                .providerOrderId("ORD-" + UUID.randomUUID())
                .providerPassword("secret")
                .amount(new BigDecimal(amount))
                .capturedAmount(captured == null ? null : new BigDecimal(captured))
                .refundedAmount(new BigDecimal(refunded))
                .status(status)
                .build());

        long delta = at.getEpochSecond() - saved.getCreatedAt().getEpochSecond();
        if (delta != 0) {
            // Сложение на диалекте PostgreSQL: `TIMESTAMPADD` — функция H2, на настоящей СУБД
            // такой запрос не разбирается вовсе. Сдвиг сохранённого значения, а не абсолютная
            // метка: так результат не зависит от того, в какой зоне драйвер закодирует время.
            // `delta` бывает любого знака, поэтому именно плюс.
            jdbcTemplate.update(
                    "UPDATE transactions SET created_at = created_at + CAST(? AS double precision) "
                            + "* INTERVAL '1 second' WHERE id = ?",
                    (double) delta, saved.getId());
        }
        return saved;
    }

    private JsonNode summary(String token, String query) throws Exception {
        return exact(mockMvc.perform(get("/api/v1/dashboard/summary" + query)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    // Разбор с сохранением масштаба. Обе настройки обязательны: без первой Jackson читает
    // дробные в double («100.00» → 100.0), без второй JsonNodeFactory обрезает хвостовые нули
    // («100.00» → 1E+2). Двузначность копеек в ответе — часть контракта, и проверять её надо
    // на том, что реально отдал сервис.
    private static final ObjectMapper EXACT = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .nodeFactory(JsonNodeFactory.withExactBigDecimals(true))
            .build();

    private JsonNode exact(String json) throws Exception {
        return EXACT.readTree(json);
    }

    private static JsonNode totals(JsonNode body, String currency) {
        for (JsonNode row : body.get("totals")) {
            if (currency.equals(row.get("currency").asText())) {
                return row;
            }
        }
        throw new AssertionError("no totals for " + currency + " in " + body.get("totals"));
    }

    private static BigDecimal amount(JsonNode row, String field) {
        return new BigDecimal(row.get(field).asText());
    }

    // Пояс отчёта, заведомо не совпадающий с поясом машины разработчика: на бакинской JVM
    // подмена пояса отчёта системным была бы неотличима, и сторож выше её бы не поймал.
    // Токио (+09:00, без перехода на летнее время) отличается от любого европейского пояса.
    //
    // Это отдельный Spring-контекст, поэтому запрос идёт через **его** MockMvc: через внешний
    // ответил бы контекст с Asia/Baku, и тест проверял бы не то, что нужно. Окружающая фикстура
    // (setup, seed, токены) переиспользуется как есть — оба контекста делят одну H2.
    @Nested
    @SpringBootTest(properties = "pbl.dashboard.zone=Asia/Tokyo")
    @AutoConfigureMockMvc
    @Import(StubAcquirerConfig.class)
    class WithForeignReportZone {

        private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

        @Autowired
        private MockMvc tokyoMvc;

        @Test
        public void bucketsFollowTheConfiguredZone_notTheMachineZone() throws Exception {
            LocalDate before = LocalDate.now(TOKYO).minusDays(2);
            LocalDate after = LocalDate.now(TOKYO).minusDays(1);
            PaymentLink link = link(TERMINAL_A, "AZN");
            seed(link, TransactionStatus.SUCCESS, "100.00", null, "0.00",
                    before.atTime(LocalTime.of(23, 30)).atZone(TOKYO).toInstant());
            seed(link, TransactionStatus.SUCCESS, "100.00", null, "0.00",
                    after.atTime(LocalTime.of(0, 30)).atZone(TOKYO).toInstant());

            JsonNode body = exact(tokyoMvc.perform(get("/api/v1/dashboard/summary")
                            .header(HttpHeaders.AUTHORIZATION, headAToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            Assertions.assertEquals("Asia/Tokyo", body.get("window").get("zone").asText(),
                    "the answer names the zone it counted in");
            for (JsonNode day : body.get("dailyTotals")) {
                LocalDate date = LocalDate.parse(day.get("date").asText());
                long expected = date.equals(before) || date.equals(after) ? 1 : 0;
                Assertions.assertEquals(expected, day.get("transactionCount").asLong(),
                        "days are cut in the configured zone, not the machine's, " + date);
            }
        }
    }
}
