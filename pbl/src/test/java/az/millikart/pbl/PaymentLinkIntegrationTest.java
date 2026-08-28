package az.millikart.pbl;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import az.millikart.pbl.provider.StubAcquirerConfig;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRepository;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(StubAcquirerConfig.class)
class PaymentLinkIntegrationTest {

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
    private PlatformTransactionManager transactionManager;

    // Эквайер подменён моком, который по умолчанию делегирует StubAcquiringClient: поведение дубля
    // сохраняется, а тесты, которым нужен свой ответ, переопределяют один метод. Зарегистрирован
    // здесь, а не выбирается свойством: стаб перестал быть продакшн-бином 20.08.2026.
    @Autowired
    private AcquiringClient acquiringClient;

    private static final int TERMINAL_ID = 123456789;
    private static final int FOREIGN_TERMINAL_ID = 987654321;

    // Повторяет pbl.link.default-ttl тестового профиля, который повторяет продакшн.
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private String adminToken;
    private String headToken;
    private String employeeToken;
    private String auditorToken;
    private String globalAuditorToken;
    private String foreignToken;
    private String employeeWithoutCompanyToken;
    private String unknownRoleToken;

    @BeforeEach
    void cleanUp() {
        // Бин провайдера объявлен этим классом, а не @MockBean, поэтому слушатель Mockito не
        // сбрасывает его между методами и вызовы утекли бы в verify(..., never()) следующего теста.
        Mockito.reset(acquiringClient);

        transactionRepository.deleteAll();
        paymentLinkRepository.deleteAll();
        terminalRepository.deleteAll();

        Terminal terminal = Terminal.builder()
                .id(TERMINAL_ID)
                .name("Test Terminal")
                .login("TerminalSys/Admin")
                .password("1234")
                .companyId("test-company")
                .build();
        terminalRepository.save(terminal);

        Terminal foreignTerminal = Terminal.builder()
                .id(FOREIGN_TERMINAL_ID)
                .name("Other Company Terminal")
                .login("TerminalSys/Other")
                .password("4321")
                .companyId("other-company")
                .build();
        terminalRepository.save(foreignTerminal);

        adminToken = createMockJwtToken("admin-user", "SYSTEM_ADMIN", null);
        headToken = createMockJwtToken("head-user", "COMPANY_HEAD", "test-company");
        employeeToken = createMockJwtToken("emp-user", "COMPANY_EMPLOYEE", "test-company");
        auditorToken = createMockJwtToken("aud-user", "AUDITOR", "test-company");
        globalAuditorToken = createMockJwtToken("aud2", "AUDITOR", null);
        foreignToken = createMockJwtToken("other-user", "COMPANY_HEAD", "other-company");
        employeeWithoutCompanyToken = createMockJwtToken("emp-no-company", "COMPANY_EMPLOYEE", null);
        unknownRoleToken = createMockJwtToken("hacker-user", "HACKER", "test-company");
    }

    private String createMockJwtToken(String userId, String role, String companyId) {
        return "Bearer " + jwtProvider.generateToken(userId, userId + "@test.com", role, companyId);
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, token);
    }

    private ObjectNode validCreateRequest() {
        ObjectNode customer = objectMapper.createObjectNode();
        customer.put("fullName", "John Doe");
        customer.put("email", "test@test.com");
        customer.put("phone", "994509771884");

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("campaign", "summer_sale");

        ObjectNode request = objectMapper.createObjectNode();
        request.put("merchantOrderId", "ORDER-12345");
        request.put("terminal", TERMINAL_ID);
        request.put("amount", new BigDecimal("1500.50"));
        request.put("currency", "AZN");
        request.put("description", "Payment for order #123456");
        request.set("customer", customer);
        request.put("paymentType", "DMS");
        request.put("usageType", "MULTIPLE");
        request.put("maxPayments", 25);
        request.set("metadata", metadata);
        return request;
    }

    private ObjectNode smsSingleCreateRequest() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("merchantOrderId", "ORDER-SMS-1");
        request.put("terminal", TERMINAL_ID);
        request.put("amount", new BigDecimal("1500.50"));
        request.put("currency", "AZN");
        request.put("paymentType", "SMS");
        request.put("usageType", "SINGLE");
        return request;
    }

    @Test
    void createPaymentLink_returns201WithBody() throws Exception {
        mockMvc.perform(authed(post("/api/v1/payment-links"), employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.terminal", is(123456789)))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.link", containsString("/open")))
                // P1-9: созданная ссылка всегда несёт момент, когда перестаёт быть оплачиваемой.
                .andExpect(jsonPath("$.expiresAt", notNullValue()));
    }

    @Test
    void createPaymentLink_asAuditor_returns403() throws Exception {
        mockMvc.perform(authed(post("/api/v1/payment-links"), auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPaymentLink_companyMismatch_returns403() throws Exception {
        mockMvc.perform(authed(post("/api/v1/payment-links"), foreignToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPaymentLink_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/payment-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPaymentLink_missingTerminal_returns400() throws Exception {
        ObjectNode request = validCreateRequest();
        request.remove("terminal");

        mockMvc.perform(authed(post("/api/v1/payment-links"), headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPaymentLink_returnsLink() throws Exception {
        UUID id = createLinkAndGetId(headToken);

        mockMvc.perform(authed(get("/api/v1/payment-links/{id}", id), employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id.toString())))
                .andExpect(jsonPath("$.amount", is(1500.50)));
    }

    @Test
    void getPaymentLink_companyMismatch_returns403() throws Exception {
        UUID id = createLinkAndGetId(headToken);

        mockMvc.perform(authed(get("/api/v1/payment-links/{id}", id), foreignToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listPaymentLinks_returnsPagedResult() throws Exception {
        createLinkAndGetId(headToken);

        mockMvc.perform(authed(get("/api/v1/payment-links"), headToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void listPaymentLinks_forForeignCompany_returnsEmpty() throws Exception {
        createLinkAndGetId(headToken);

        mockMvc.perform(authed(get("/api/v1/payment-links"), foreignToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    void updatePaymentLink_appliesChanges() throws Exception {
        UUID id = createLinkAndGetId(headToken);

        ObjectNode update = objectMapper.createObjectNode();
        update.put("amount", new BigDecimal("1600.00"));
        update.put("status", "CANCELED");

        mockMvc.perform(authed(patch("/api/v1/payment-links/{id}", id), employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(1600.00)))
                .andExpect(jsonPath("$.status", is("CANCELED")));
    }

    // ---------------------------------------------------------------------------------------
    // P2-9: что правка может и чего не может сделать со ссылкой, уже ушедшей в мир.
    // ---------------------------------------------------------------------------------------

    // Ссылка, которую никто не пытался оплатить, — всё ещё черновик, и её цена свободна.
    @Test
    void updateAmount_onLinkWithoutTransactions_isApplied() throws Exception {
        PaymentLink link = linkFixture(UsageType.MULTIPLE, 5, Instant.now().plus(DEFAULT_TTL));

        patchLink(link.getId(), amountUpdate("250.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(250.00)));

        Assertions.assertEquals(0, new BigDecimal("250.00")
                .compareTo(paymentLinkRepository.findById(link.getId()).orElseThrow().getAmount()));
    }

    // Главный случай: любой статус, в котором деньги были в игре, замораживает сумму. PENDING тоже:
    // это плательщик, стоящий на странице оплаты прямо сейчас, и показанную ему цену менять нельзя.
    @ParameterizedTest(name = "amount is frozen by a {0} attempt")
    @EnumSource(value = TransactionStatus.class,
            names = {"PENDING", "AUTHORIZED", "SUCCESS", "PARTIALLY_REFUNDED", "REFUNDED"})
    void updateAmount_onLinkWithAnAttempt_returns400(TransactionStatus status) throws Exception {
        PaymentLink link = linkFixture(UsageType.MULTIPLE, 5, Instant.now().plus(DEFAULT_TTL));
        attemptFixture(link, status);

        patchLink(link.getId(), amountUpdate("250.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already has payments")));

        Assertions.assertEquals(0, new BigDecimal("100.00")
                .compareTo(paymentLinkRepository.findById(link.getId()).orElseThrow().getAmount()));
    }

    // Неудавшаяся попытка — не договорённость о цене, она ничего не блокирует.
    @Test
    void updateAmount_onLinkWithOnlyFailedAttempts_isApplied() throws Exception {
        PaymentLink link = linkFixture(UsageType.MULTIPLE, 5, Instant.now().plus(DEFAULT_TTL));
        attemptFixture(link, TransactionStatus.FAILED);
        attemptFixture(link, TransactionStatus.FAILED);

        patchLink(link.getId(), amountUpdate("250.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(250.00)));
    }

    // Портал PATCH'ит форму целиком, вместе с суммой. Присланная прежняя сумма — не правка и не
    // должна ронять заморозку.
    @Test
    void updateAmount_withTheSameValueOnAPaidLink_returns200() throws Exception {
        PaymentLink link = linkFixture(UsageType.MULTIPLE, 5, Instant.now().plus(DEFAULT_TTL));
        attemptFixture(link, TransactionStatus.SUCCESS);

        ObjectNode update = amountUpdate("100.00");
        update.put("description", "Edited while paid");

        patchLink(link.getId(), update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is("Edited while paid")));
    }

    // Снятие отмены — единственный путь обратно в ACTIVE, и только пока у ссылки есть время.
    @Test
    void updateStatus_canceledToActive_withFutureExpiry_returns200() throws Exception {
        PaymentLink link = linkFixtureInStatus(PaymentLinkStatus.CANCELED, Instant.now().plus(DEFAULT_TTL));

        patchLink(link.getId(), statusUpdate("ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        Assertions.assertEquals(PaymentLinkStatus.ACTIVE,
                paymentLinkRepository.findById(link.getId()).orElseThrow().getStatus());
    }

    // Снять отмену со ссылки с прошедшим сроком значило бы получить ACTIVE-ссылку, которую никто не
    // может оплатить, пока свёртка снова не пометит её EXPIRED. В отказе сказано, как это чинить.
    @Test
    void updateStatus_canceledToActive_withPastExpiry_returns400() throws Exception {
        PaymentLink link = linkFixtureInStatus(PaymentLinkStatus.CANCELED, Instant.now().minus(Duration.ofMinutes(1)));

        patchLink(link.getId(), statusUpdate("ACTIVE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("expiresAt")));

        Assertions.assertEquals(PaymentLinkStatus.CANCELED,
                paymentLinkRepository.findById(link.getId()).orElseThrow().getStatus());
    }

    // Тот самый способ, подсказанный отказом выше, в одном запросе: expiresAt применяется до
    // статуса, поэтому реактивация судится уже по новому сроку.
    @Test
    void updateStatus_canceledToActive_withANewExpiryInTheSameRequest_returns200() throws Exception {
        PaymentLink link = linkFixtureInStatus(PaymentLinkStatus.CANCELED, Instant.now().minus(Duration.ofMinutes(1)));
        Instant revived = Instant.now().plus(Duration.ofDays(5)).truncatedTo(ChronoUnit.SECONDS);

        ObjectNode update = statusUpdate("ACTIVE");
        update.put("expiresAt", revived.toString());

        patchLink(link.getId(), update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        PaymentLink stored = paymentLinkRepository.findById(link.getId()).orElseThrow();
        Assertions.assertEquals(PaymentLinkStatus.ACTIVE, stored.getStatus());
        Assertions.assertEquals(revived, stored.getExpiresAt());
    }

    // Срок и лимит использований, которые один PATCH мог бы отменить, не ограничивали бы ничего,
    // поэтому ни истёкшая, ни исчерпанная ссылка не возвращается в ACTIVE.
    @ParameterizedTest(name = "{0} -> ACTIVE is refused")
    @EnumSource(value = PaymentLinkStatus.class, names = {"EXPIRED", "COMPLETED"})
    void updateStatus_toActive_fromADeadStatus_returns400(PaymentLinkStatus from) throws Exception {
        PaymentLink link = linkFixtureInStatus(from, Instant.now().plus(DEFAULT_TTL));

        patchLink(link.getId(), statusUpdate("ACTIVE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString(from + " to ACTIVE")));

        Assertions.assertEquals(from, paymentLinkRepository.findById(link.getId()).orElseThrow().getStatus());
    }

    // COMPLETED — факт о случившемся, а не настройка: из него не ведёт ничего, включая отмену.
    @Test
    void updateStatus_completedToCanceled_returns400() throws Exception {
        PaymentLink link = linkFixtureInStatus(PaymentLinkStatus.COMPLETED, Instant.now().plus(DEFAULT_TTL));

        patchLink(link.getId(), statusUpdate("CANCELED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("COMPLETED to CANCELED")));

        Assertions.assertEquals(PaymentLinkStatus.COMPLETED,
                paymentLinkRepository.findById(link.getId()).orElseThrow().getStatus());
    }

    // Отмена доступна из обоих живых состояний — истёкшая ссылка это мусор мерчанта.
    @ParameterizedTest(name = "{0} -> CANCELED is allowed")
    @EnumSource(value = PaymentLinkStatus.class, names = {"ACTIVE", "EXPIRED"})
    void updateStatus_toCanceled_returns200(PaymentLinkStatus from) throws Exception {
        PaymentLink link = linkFixtureInStatus(from, Instant.now().plus(DEFAULT_TTL));

        patchLink(link.getId(), statusUpdate("CANCELED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELED")));

        Assertions.assertEquals(PaymentLinkStatus.CANCELED,
                paymentLinkRepository.findById(link.getId()).orElseThrow().getStatus());
    }

    // Присланный текущий статус — не правка, в том числе из тупикового статуса.
    @ParameterizedTest(name = "{0} -> {0} is a no-op")
    @EnumSource(PaymentLinkStatus.class)
    void updateStatus_toTheCurrentStatus_returns200(PaymentLinkStatus current) throws Exception {
        PaymentLink link = linkFixtureInStatus(current, Instant.now().plus(DEFAULT_TTL));

        patchLink(link.getId(), statusUpdate(current.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(current.name())));

        Assertions.assertEquals(current, paymentLinkRepository.findById(link.getId()).orElseThrow().getStatus());
    }

    // Лимит ниже уже принятых платежей оставил бы ссылку с надписью "использовано 3 из 2".
    @Test
    void updateMaxPayments_belowTheSuccessfulPayments_returns400() throws Exception {
        PaymentLink link = linkFixture(UsageType.MULTIPLE, 5, Instant.now().plus(DEFAULT_TTL));
        attemptFixture(link, TransactionStatus.SUCCESS);
        attemptFixture(link, TransactionStatus.SUCCESS);
        attemptFixture(link, TransactionStatus.SUCCESS);

        ObjectNode update = objectMapper.createObjectNode();
        update.put("maxPayments", 2);

        patchLink(link.getId(), update)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("2")))
                .andExpect(jsonPath("$.message", containsString("3")));

        Assertions.assertEquals(5, paymentLinkRepository.findById(link.getId()).orElseThrow().getMaxPayments());
    }

    // Закрыть ссылку ровно на том, что она уже приняла, — законный способ её вывести.
    @Test
    void updateMaxPayments_equalToTheSuccessfulPayments_returns200() throws Exception {
        PaymentLink link = linkFixture(UsageType.MULTIPLE, 5, Instant.now().plus(DEFAULT_TTL));
        attemptFixture(link, TransactionStatus.SUCCESS);
        attemptFixture(link, TransactionStatus.SUCCESS);

        ObjectNode update = objectMapper.createObjectNode();
        update.put("maxPayments", 2);

        patchLink(link.getId(), update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPayments", is(2)));
    }

    private ResultActions patchLink(UUID id, ObjectNode body) throws Exception {
        return mockMvc.perform(authed(patch("/api/v1/payment-links/{id}", id), headToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ObjectNode amountUpdate(String amount) {
        ObjectNode update = objectMapper.createObjectNode();
        update.put("amount", new BigDecimal(amount));
        return update;
    }

    private ObjectNode statusUpdate(String status) {
        ObjectNode update = objectMapper.createObjectNode();
        update.put("status", status);
        return update;
    }

    @Test
    void openPaymentLink_redirectsToProvider() throws Exception {
        UUID id = createLinkAndGetId(headToken);

        // Публичный эндпоинт, авторизация не нужна.
        mockMvc.perform(get("/api/v1/payment-links/{id}/open", id))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("rid=")));
    }

    // --- P0-9: order password не попадает ни в provider_response, ни в лог ------------------

    // Открытие ссылки пишет ответ эквайера в provider_response. У order password своя колонка,
    // provider_password, из которой читают последующие вызовы; JSON-колонка — след для разборов и
    // не должна нести пароль второй раз. Проверяем сырую колонку, чтобы ничто по пути не спрятало.
    @Test
    void openPaymentLink_storesThePasswordInItsOwnColumnOnly() throws Exception {
        UUID id = createLinkAndGetId(headToken);

        mockMvc.perform(get("/api/v1/payment-links/{id}/open", id))
                .andExpect(status().isFound());

        Transaction pending = transactionRepository.findAll().getFirst();
        Assertions.assertFalse(pending.getProviderPassword() == null || pending.getProviderPassword().isBlank(),
                "provider_password is where the password belongs");
        Map<String, Object> providerResponse = pending.getProviderResponse();
        Assertions.assertFalse(providerResponse.containsKey("password"), "no password in provider_response: " + providerResponse);
        Assertions.assertNotNull(providerResponse.get("hppUrl"), "the rest of the answer stays: " + providerResponse);
        Assertions.assertNotNull(providerResponse.get("id"));
        Assertions.assertNotNull(providerResponse.get("status"));

        String rawColumn = jdbcTemplate.queryForObject(
                "SELECT provider_response FROM transactions WHERE id = ?", String.class, pending.getId());
        Assertions.assertFalse(rawColumn.contains("password"), "raw provider_response: " + rawColumn);
        Assertions.assertFalse(rawColumn.contains(pending.getProviderPassword()), "raw provider_response: " + rawColumn);
    }

    // Плательщика редиректят на HPP с паролем в query — так и открывается страница оплаты, — но сам
    // редирект логировать как есть нельзя. Ловим на root-логгере: контроллер, сервис и провайдер.
    @Test
    void openPaymentLink_redirectCarriesThePassword_butNoLogLineDoes() throws Exception {
        UUID id = createLinkAndGetId(headToken);
        ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
        logEvents.start();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(logEvents);
        try {
            String location = mockMvc.perform(get("/api/v1/payment-links/{id}/open", id))
                    .andExpect(status().isFound())
                    .andReturn().getResponse().getHeader("Location");

            Transaction pending = transactionRepository.findAll().getFirst();
            String password = pending.getProviderPassword();
            Assertions.assertNotNull(location);
            Assertions.assertTrue(location.contains("password=" + password), "the payer's redirect needs the password: " + location);

            List<String> offending = logEvents.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.contains(password))
                    .toList();
            Assertions.assertTrue(offending.isEmpty(), "the order password reached the log: " + offending);
            Assertions.assertTrue(logEvents.list.stream().map(ILoggingEvent::getFormattedMessage)
                            .anyMatch(message -> message.startsWith("Redirecting customer to HPP URL: ")),
                    "the redirect line itself must still be logged (without its query string)");
        } finally {
            root.detachAppender(logEvents);
        }
    }

    // Каждый опрос статуса сохраняет order-payload эквайера, а при orderDetailLevel=2 в нём есть
    // password (§5.8.3). Срезать его надо на каждом опросе, а не только при создании.
    @Test
    void checkStatus_payloadWithPassword_storesItWithoutThePassword() throws Exception {
        Transaction pending = createTransaction(TERMINAL_ID, "TX-POLL-PASSWORD", TransactionStatus.PENDING);
        doReturn(Map.of(
                "id", 11338,
                "hppUrl", "https://test.millikart.az:8004",
                "password", "1h1pq153fk8xk",
                "status", "FullyPaid",
                "ridByMerchant", "123123871283618376123",
                "amount", 5))
                .when(acquiringClient).getOrderStatus(anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(authed(get("/api/v1/transactions/{identifier}/status", pending.getId()), headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")));

        Map<String, Object> stored = transactionRepository.findById(pending.getId()).orElseThrow().getProviderResponse();
        Assertions.assertFalse(stored.containsKey("password"), "no password in provider_response: " + stored);
        Assertions.assertEquals("FullyPaid", stored.get("status"));
        Assertions.assertEquals("https://test.millikart.az:8004", stored.get("hppUrl"));
        Assertions.assertEquals("123123871283618376123", stored.get("ridByMerchant"));
        Assertions.assertEquals("11338", String.valueOf(stored.get("id")));
        Assertions.assertEquals("PAID", stored.get("mpStatusOutcome"), "this service's own markers stay: " + stored);

        String rawColumn = jdbcTemplate.queryForObject(
                "SELECT provider_response FROM transactions WHERE id = ?", String.class, pending.getId());
        Assertions.assertFalse(rawColumn.contains("1h1pq153fk8xk"), "raw provider_response: " + rawColumn);
    }

    // Пустой ответ опроса сохраняет прежний payload (P1-8a), а строка, записанная до P0-9, может ещё
    // нести password тех времён. Сохраняемый payload идёт через ту же чистку, и старая утечка
    // закрывается на следующем опросе, а не копируется вперёд вечно.
    @Test
    void checkStatus_emptyAnswer_keepsThePreviousPayloadWithoutThePassword() throws Exception {
        Transaction pending = createTransaction(TERMINAL_ID, "TX-LEGACY-PASSWORD", TransactionStatus.PENDING);
        pending.setProviderResponse(Map.of(
                "hppUrl", "https://test.millikart.az:8004",
                "status", "Preparing",
                "password", "legacy-secret"));
        transactionRepository.save(pending);
        doReturn(null)
                .when(acquiringClient).getOrderStatus(anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(authed(get("/api/v1/transactions/{identifier}/status", pending.getId()), headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING")));

        Map<String, Object> stored = transactionRepository.findById(pending.getId()).orElseThrow().getProviderResponse();
        Assertions.assertFalse(stored.containsKey("password"), "the old password must not be copied forward: " + stored);
        Assertions.assertEquals("https://test.millikart.az:8004", stored.get("hppUrl"), "the rest of the old payload survives");
        Assertions.assertEquals("Preparing", stored.get("status"));
        Assertions.assertEquals("UNKNOWN", stored.get("mpStatusOutcome"));
    }

    // P1-8a: PENDING-capture сначала опрашивает эквайера; неизвестное сервису слово статуса обязано
    // отклонить capture (400) и не отправлять клиринг — ради этого опрос и существует. Та же форма,
    // что у MoneyOperationsIntegrationTest.completeDms_stillPendingAtProvider_returns400, но со
    // словом вне словаря, а не с известным нефинальным.
    @Test
    void completeDms_pendingAndProviderSaysUnknownStatus_returns400AndDoesNotCapture() throws Exception {
        Transaction pending = createTransaction(TERMINAL_ID, "DMS-UNKNOWN", TransactionStatus.PENDING);
        doReturn(Map.of("status", "Settled"))
                .when(acquiringClient).getOrderStatus(anyString(), anyString(), anyString(), anyString());

        ObjectNode complete = objectMapper.createObjectNode();
        complete.put("amount", new BigDecimal("100.00"));

        mockMvc.perform(authed(post("/api/v1/transactions/{id}/complete", pending.getId()), headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complete)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("has not been authorized by the acquirer yet")));

        verify(acquiringClient, never()).completeDms(anyString(), anyString(), anyString(), anyString(), any());
        Assertions.assertEquals(TransactionStatus.PENDING,
                transactionRepository.findById(pending.getId()).orElseThrow().getStatus());
    }

    @Test
    void completeDmsAndRefundFlow() throws Exception {
        UUID id = createLinkAndGetId(headToken);

        mockMvc.perform(get("/api/v1/payment-links/{id}/open", id))
                .andExpect(status().isFound());

        List<Transaction> transactions = transactionRepository.findAll();
        Transaction pending = transactions.getFirst();

        pending.setStatus(TransactionStatus.AUTHORIZED);
        transactionRepository.save(pending);

        ObjectNode complete = objectMapper.createObjectNode();
        complete.put("amount", new BigDecimal("1500.50"));

        mockMvc.perform(authed(post("/api/v1/transactions/{id}/complete", pending.getId()), employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complete)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        // Сотруднику возврат недоступен.
        ObjectNode refund = objectMapper.createObjectNode();
        refund.put("amount", new BigDecimal("500.00"));
        refund.put("reason", "Customer request");

        mockMvc.perform(authed(post("/api/v1/transactions/{id}/refund", pending.getId()), employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refund)))
                .andExpect(status().isForbidden());

        // Head/Admin возврат может. Это единственный путь, который гоняет реальный стаб (P1-8b):
        // он обязан отвечать в форме контракта, чтобы подтверждение было на месте.
        mockMvc.perform(authed(post("/api/v1/transactions/{id}/refund", pending.getId()), headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refund)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PARTIALLY_REFUNDED")))
                .andExpect(jsonPath("$.amount", is(500.00)))
                .andExpect(jsonPath("$.acquirerReference", not(emptyOrNullString())))
                .andExpect(jsonPath("$.refundId", not(emptyOrNullString())));

        Map<String, Object> providerResponse = transactionRepository.findById(pending.getId()).orElseThrow().getProviderResponse();
        Assertions.assertInstanceOf(Map.class, providerResponse.get("mpCapture"), "stub capture leaves evidence: " + providerResponse);
        Assertions.assertNotNull(((Map<?, ?>) providerResponse.get("mpCapture")).get("ridByPmo"));
        Assertions.assertInstanceOf(List.class, providerResponse.get("mpRefunds"), "stub refund leaves evidence: " + providerResponse);
        Assertions.assertEquals(1, ((List<?>) providerResponse.get("mpRefunds")).size());
    }

    @Test
    void smsPayment_statusCheckMovesTransactionAndLinkToTerminalState() throws Exception {
        String response = mockMvc.perform(authed(post("/api/v1/payment-links"), headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(smsSingleCreateRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mockMvc.perform(get("/api/v1/payment-links/{id}/open", id))
                .andExpect(status().isFound());

        Transaction pending = transactionRepository.findAll().getFirst();

        // Мерчант опрашивает статус: эндпоинт аутентифицирован и ограничен компанией.
        mockMvc.perform(authed(get("/api/v1/transactions/{identifier}/status", pending.getProviderOrderId()), headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")));

        Transaction settled = transactionRepository.findById(pending.getId()).orElseThrow();
        Assertions.assertEquals(TransactionStatus.SUCCESS, settled.getStatus());

        PaymentLink link = paymentLinkRepository.findById(id).orElseThrow();
        Assertions.assertEquals(PaymentLinkStatus.COMPLETED, link.getStatus());
    }

    @Test
    void listTransactions_asCompanyHead_returnsOnlyOwnCompanyTransactions() throws Exception {
        Transaction own = createTransaction(TERMINAL_ID, "TX-OWN");
        createTransaction(FOREIGN_TERMINAL_ID, "TX-FOREIGN");

        mockMvc.perform(authed(get("/api/v1/transactions"), headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content.length()", is(1)))
                .andExpect(jsonPath("$.content[0].id", is(own.getId().toString())))
                .andExpect(jsonPath("$.content[0].terminalId", is(TERMINAL_ID)));
    }

    @Test
    void listTransactions_asForeignCompanyHead_doesNotSeeOtherCompany() throws Exception {
        createTransaction(TERMINAL_ID, "TX-OWN");
        Transaction foreign = createTransaction(FOREIGN_TERMINAL_ID, "TX-FOREIGN");

        mockMvc.perform(authed(get("/api/v1/transactions"), foreignToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content.length()", is(1)))
                .andExpect(jsonPath("$.content[0].id", is(foreign.getId().toString())))
                .andExpect(jsonPath("$.content[0].terminalId", is(FOREIGN_TERMINAL_ID)));
    }

    @Test
    void listTransactions_asSystemAdmin_returnsAll() throws Exception {
        createTransaction(TERMINAL_ID, "TX-OWN");
        createTransaction(FOREIGN_TERMINAL_ID, "TX-FOREIGN");

        mockMvc.perform(authed(get("/api/v1/transactions"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content.length()", is(2)));
    }

    @Test
    void listTransactions_asAuditorWithoutCompany_returnsAll() throws Exception {
        createTransaction(TERMINAL_ID, "TX-OWN");
        createTransaction(FOREIGN_TERMINAL_ID, "TX-FOREIGN");

        mockMvc.perform(authed(get("/api/v1/transactions"), globalAuditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content.length()", is(2)));
    }

    @Test
    void listTransactions_asEmployeeWithoutCompany_returnsEmpty() throws Exception {
        createTransaction(TERMINAL_ID, "TX-OWN");

        mockMvc.perform(authed(get("/api/v1/transactions"), employeeWithoutCompanyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)))
                .andExpect(jsonPath("$.content.length()", is(0)));
    }

    @Test
    void listTransactions_withUnknownRole_returns403() throws Exception {
        createTransaction(TERMINAL_ID, "TX-OWN");

        mockMvc.perform(authed(get("/api/v1/transactions"), unknownRoleToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTransactions_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isUnauthorized());
    }

    // --- P0-4: GET /payment-links/{id}/transactions читает компания-владелец -----------------

    // Раньше здесь был 403 для всех, кроме SYSTEM_ADMIN: эндпоинт стерегли роли MERCHANT_ADMIN и
    // MERCHANT_USER, которых в этой системе не существует.
    @Test
    void getLinkTransactions_asCompanyHead_returnsTransactions() throws Exception {
        Transaction tx = createTransaction(TERMINAL_ID, "TX-LINK-OWN");

        mockMvc.perform(authed(get("/api/v1/payment-links/{id}/transactions", tx.getLink().getId()), headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].id", is(tx.getId().toString())))
                .andExpect(jsonPath("$[0].terminalId", is(TERMINAL_ID)));
    }

    @Test
    void getLinkTransactions_asForeignCompany_returns403() throws Exception {
        Transaction tx = createTransaction(TERMINAL_ID, "TX-LINK-OWN");

        mockMvc.perform(authed(get("/api/v1/payment-links/{id}/transactions", tx.getLink().getId()), foreignToken))
                .andExpect(status().isForbidden());
    }

    // У системного аудитора нет companyId; он читает поперёк компаний, как SYSTEM_ADMIN.
    @Test
    void getLinkTransactions_asGlobalAuditor_returnsTransactions() throws Exception {
        Transaction tx = createTransaction(FOREIGN_TERMINAL_ID, "TX-LINK-FOREIGN");

        mockMvc.perform(authed(get("/api/v1/payment-links/{id}/transactions", tx.getLink().getId()), globalAuditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].id", is(tx.getId().toString())));
    }

    @Test
    void getLinkTransactions_withUnknownRole_returns403() throws Exception {
        Transaction tx = createTransaction(TERMINAL_ID, "TX-LINK-OWN");

        mockMvc.perform(authed(get("/api/v1/payment-links/{id}/transactions", tx.getLink().getId()), unknownRoleToken))
                .andExpect(status().isForbidden());
    }

    // --- P0-2: /transactions/{id}/status доступен только мерчанту ---------------------------

    @Test
    void checkStatus_withoutToken_returns401() throws Exception {
        Transaction tx = createTransaction(TERMINAL_ID, "TX-OWN");

        mockMvc.perform(get("/api/v1/transactions/{identifier}/status", tx.getId()))
                .andExpect(status().isUnauthorized());

        // Через этот эндпоинт раньше можно было перебирать и provider order id.
        mockMvc.perform(get("/api/v1/transactions/{identifier}/status", tx.getProviderOrderId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkStatus_asForeignCompany_returns403() throws Exception {
        Transaction tx = createTransaction(TERMINAL_ID, "TX-OWN");

        mockMvc.perform(authed(get("/api/v1/transactions/{identifier}/status", tx.getId()), foreignToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void checkStatus_asOwnCompany_returns200() throws Exception {
        Transaction tx = createTransaction(TERMINAL_ID, "TX-OWN");

        mockMvc.perform(authed(get("/api/v1/transactions/{identifier}/status", tx.getId()), headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(tx.getId().toString())))
                .andExpect(jsonPath("$.status", is("SUCCESS")));
    }

    // P1-8b: эквайер говорит, почему отказал (custAttrs, §5.8.7), и мерчант получает эту причину
    // как failureReason вместо голого FAILED. Payload повторяет §5.8.3, включая PmoResultCode,
    // который нельзя принять за причину.
    @Test
    void checkStatus_rejectedWithDeclineDescription_returnsFailureReason() throws Exception {
        Transaction pending = createTransaction(TERMINAL_ID, "TX-DECLINED", TransactionStatus.PENDING);
        doReturn(Map.of(
                "status", "Rejected",
                "custAttrs", List.of(
                        Map.of("rid", "PrevStatus", "valAsStr", "Preparing"),
                        Map.of("rid", "PmoResultCode", "valAsStr", "05"),
                        Map.of("rid", "DeclineDescription", "valAsStr", "Invalid PAN"),
                        Map.of("rid", "PmoDeclineDescription", "valAsStr", "Invalid cvv2 for this card."))))
                .when(acquiringClient).getOrderStatus(anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(authed(get("/api/v1/transactions/{identifier}/status", pending.getId()), headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.failureReason", is("Invalid PAN")));

        Transaction failed = transactionRepository.findById(pending.getId()).orElseThrow();
        Assertions.assertEquals(TransactionStatus.FAILED, failed.getStatus());
        Assertions.assertEquals("Invalid PAN", failed.getProviderResponse().get("mpDeclineReason"));

        // Второе чтение отдаёт сохранённую причину: FAILED финален, эквайера больше не спрашивают.
        mockMvc.perform(authed(get("/api/v1/transactions/{identifier}/status", pending.getId()), headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureReason", is("Invalid PAN")));
    }

    // У прошедшего платежа причины отказа нет, даже когда в payload лежит PmoResultCode: Approved.
    @Test
    void checkStatus_paidWithApprovedResultCode_hasNoFailureReason() throws Exception {
        Transaction pending = createTransaction(TERMINAL_ID, "TX-APPROVED", TransactionStatus.PENDING);
        doReturn(Map.of(
                "status", "FullyPaid",
                "custAttrs", List.of(
                        Map.of("rid", "PrevStatus", "valAsStr", "Preparing"),
                        Map.of("rid", "PmoResultCode", "valAsStr", "Approved"))))
                .when(acquiringClient).getOrderStatus(anyString(), anyString(), anyString(), anyString());

        String body = mockMvc.perform(authed(get("/api/v1/transactions/{identifier}/status", pending.getId()), headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andReturn().getResponse().getContentAsString();

        JsonNode failureReason = objectMapper.readTree(body).get("failureReason");
        Assertions.assertTrue(failureReason == null || failureReason.isNull(),
                "a paid transaction has no failure reason, got: " + failureReason);
    }

    // P1-16: маскированный номер карты, RRN и approvalCode берутся оттуда, где их держит контракт —
    // order.srcToken.displayName и запись покупки в order.trans[] (§5.8.6), — а не с верхнего уровня
    // payload, где их никогда не было. Payload — это §5.8.6 так, как его отдаёт реальный клиент.
    @Test
    void checkStatus_fullPayload_returnsMaskedCardRrnAndApprovalCode() throws Exception {
        Transaction pending = createTransaction(TERMINAL_ID, "TX-CARD-FACTS", TransactionStatus.PENDING);
        doReturn(contractOrderPayload())
                .when(acquiringClient).getOrderStatus(anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(authed(get("/api/v1/transactions/{identifier}/status", pending.getId()), headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.cardNumberMasked", is("426863******3689")))
                .andExpect(jsonPath("$.rrn", is("629677123123123123")))
                .andExpect(jsonPath("$.approvalCode", is("629677")));

        // Второе чтение отдаёт сохранённый payload: SUCCESS финален, эквайера не спрашивают снова,
        // а три факта читаются на лету из provider_response — ответ тот же.
        mockMvc.perform(authed(get("/api/v1/transactions/{identifier}/status", pending.getId()), headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardNumberMasked", is("426863******3689")))
                .andExpect(jsonPath("$.rrn", is("629677123123123123")))
                .andExpect(jsonPath("$.approvalCode", is("629677")));
        verify(acquiringClient, times(1)).getOrderStatus(anyString(), anyString(), anyString(), anyString());
    }

    // P1-16: список и карточка читают один и тот же сохранённый payload одним парсером, поэтому
    // строка в GET /api/v1/transactions показывает ровно то же, что /status: мерчант не должен
    // видеть карту на одном экране и пустое поле на другом.
    @Test
    void listTransactions_showsTheSameCardFactsAsTheStatusCard() throws Exception {
        Transaction pending = createTransaction(TERMINAL_ID, "TX-CARD-FACTS-LIST", TransactionStatus.PENDING);
        doReturn(contractOrderPayload())
                .when(acquiringClient).getOrderStatus(anyString(), anyString(), anyString(), anyString());

        String card = mockMvc.perform(authed(get("/api/v1/transactions/{identifier}/status", pending.getId()), headToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode fromCard = objectMapper.readTree(card);

        String list = mockMvc.perform(authed(get("/api/v1/transactions"), headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is(pending.getId().toString())))
                .andExpect(jsonPath("$.content[0].cardNumberMasked", is("426863******3689")))
                .andExpect(jsonPath("$.content[0].rrn", is("629677123123123123")))
                .andExpect(jsonPath("$.content[0].approvalCode", is("629677")))
                .andReturn().getResponse().getContentAsString();
        JsonNode fromList = objectMapper.readTree(list).get("content").get(0);

        for (String field : List.of("cardNumberMasked", "rrn", "approvalCode")) {
            Assertions.assertEquals(fromCard.get(field), fromList.get(field),
                    field + " must be the same in the list and on the card");
        }
    }

    @Test
    void checkStatus_asSystemAdmin_returns200() throws Exception {
        Transaction tx = createTransaction(FOREIGN_TERMINAL_ID, "TX-FOREIGN");

        mockMvc.perform(authed(get("/api/v1/transactions/{identifier}/status", tx.getId()), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(tx.getId().toString())));
    }

    // --- P0-2: страница возврата плательщика рендерится на сервере --------------------------

    @Test
    void redirectPage_forSuccessfulPayment_rendersReceipt() throws Exception {
        Transaction tx = createTransaction(TERMINAL_ID, "ORDER-RECEIPT", TransactionStatus.SUCCESS);

        mockMvc.perform(get("/api/v1/payment-links/redirect/{tx}", tx.getMerchantRid()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Transaction Receipt")))
                .andExpect(content().string(containsString("100.00 AZN")))
                .andExpect(content().string(containsString("ORDER-RECEIPT")))
                // Никакого клиентского кода: страница не должна сама никуда ходить.
                .andExpect(content().string(not(containsString("<script"))))
                .andExpect(content().string(not(containsString("/api/v1/transactions"))));
    }

    @Test
    void redirectPage_forPendingPayment_rendersPendingState() throws Exception {
        // Провайдер ещё не рассчитал заказ: одна проверка, дальше просто говорим об этом.
        doReturn(Map.of("status", "Preparing"))
                .when(acquiringClient).getOrderStatus(anyString(), anyString(), anyString(), anyString());

        Transaction tx = createTransaction(TERMINAL_ID, "ORDER-PENDING", TransactionStatus.PENDING);

        mockMvc.perform(get("/api/v1/payment-links/redirect/{tx}", tx.getMerchantRid()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("being processed")))
                .andExpect(content().string(not(containsString("Transaction Receipt"))))
                .andExpect(content().string(not(containsString("<script"))));

        Assertions.assertEquals(TransactionStatus.PENDING,
                transactionRepository.findById(tx.getId()).orElseThrow().getStatus());
    }

    @Test
    void redirectPage_withUnknownMerchantRid_rendersNeutralPage() throws Exception {
        mockMvc.perform(get("/api/v1/payment-links/redirect/{tx}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("unavailable")))
                .andExpect(content().string(not(containsString("Transaction Receipt"))));
    }

    @Test
    void redirectPage_withMalformedTx_rendersNeutralPage() throws Exception {
        mockMvc.perform(get("/api/v1/payment-links/redirect/{tx}", "not-a-uuid"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("unavailable")))
                .andExpect(content().string(not(containsString("Transaction Receipt"))));
    }

    @Test
    void redirectPage_ignoresProviderIdQueryParam() throws Exception {
        Transaction shown = createTransaction(TERMINAL_ID, "TX-ONE", TransactionStatus.SUCCESS);
        Transaction other = createTransaction(TERMINAL_ID, "TX-TWO", TransactionStatus.PENDING);

        // Провайдер дописывает свои ID/PASSWORD/STATUS; они под контролем атакующего и не должны ни
        // выбирать транзакцию, ни запускать обновление статуса на чужой.
        mockMvc.perform(get("/api/v1/payment-links/redirect/{tx}", shown.getMerchantRid())
                        .param("ID", other.getProviderOrderId())
                        .param("PASSWORD", "provider-password")
                        .param("STATUS", "Declined"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TX-ONE")))
                .andExpect(content().string(not(containsString("TX-TWO"))));

        Assertions.assertEquals(TransactionStatus.PENDING,
                transactionRepository.findById(other.getId()).orElseThrow().getStatus());
    }

    // --- P1-6: переоткрытие ссылки не должно трогать деньги, уже захолдированные на карте ----

    // Главный тест P1-6. AUTHORIZED-транзакция — это холд на карте плательщика; старый путь
    // переоткрытия помечал её FAILED ради новой сессии, что у эквайера не меняло ничего: холд жил,
    // деньги оставались заморожены, а снять их мерчант уже не мог — портал показывал платёж
    // неуспешным.
    @Test
    void reopen_withAuthorizedTransaction_doesNotMarkItFailed() throws Exception {
        PaymentLink link = linkFixture(UsageType.MULTIPLE, 25);
        Transaction held = attemptFixture(link, TransactionStatus.AUTHORIZED);

        mockMvc.perform(get("/api/v1/payment-links/{id}/open", link.getId()))
                .andExpect(status().isFound());

        Assertions.assertEquals(TransactionStatus.AUTHORIZED,
                transactionRepository.findById(held.getId()).orElseThrow().getStatus());
        // Новая попытка регистрируется рядом с холдом, а не поверх него.
        Assertions.assertEquals(2, transactionRepository.findByLinkIdOrderByCreatedAtDesc(link.getId()).size());
    }

    // Холд занимает единственный слот одноразовой ссылки: снять его мы не можем (операции Void
    // нет), поэтому повторное открытие означало бы два платежа по ссылке на один. В сообщении
    // сказано, какой из двух отказов это.
    @Test
    void reopen_singleUseLink_withAuthorizedTransaction_isRefused() throws Exception {
        PaymentLink link = linkFixture(UsageType.SINGLE, null);
        Transaction held = attemptFixture(link, TransactionStatus.AUTHORIZED);

        mockMvc.perform(get("/api/v1/payment-links/{id}/open", link.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Payment link has an authorized payment awaiting capture")));

        verify(acquiringClient, never()).createEcomOrder(any(), anyString(), anyString(), any(), anyString());
        Assertions.assertEquals(TransactionStatus.AUTHORIZED,
                transactionRepository.findById(held.getId()).orElseThrow().getStatus());
    }

    // PENDING-попытка — это заказ, который никто не оплатил: переоткрытие гасит её и идёт дальше.
    @Test
    void reopen_withPendingTransaction_marksItFailedAndProceeds() throws Exception {
        PaymentLink link = linkFixture(UsageType.SINGLE, null);
        Transaction abandoned = attemptFixture(link, TransactionStatus.PENDING);

        mockMvc.perform(get("/api/v1/payment-links/{id}/open", link.getId()))
                .andExpect(status().isFound());

        Assertions.assertEquals(TransactionStatus.FAILED,
                transactionRepository.findById(abandoned.getId()).orElseThrow().getStatus());

        List<Transaction> attempts = transactionRepository.findByLinkIdOrderByCreatedAtDesc(link.getId());
        Assertions.assertEquals(2, attempts.size());
        Assertions.assertEquals(TransactionStatus.PENDING, attempts.getFirst().getStatus());
    }

    // Та же арифметика слотов на многоразовой ссылке: холд занимает слот так же, как платёж.
    @Test
    void multiUseLink_authorizedCountsTowardsLimit() throws Exception {
        PaymentLink link = linkFixture(UsageType.MULTIPLE, 1);
        attemptFixture(link, TransactionStatus.AUTHORIZED);

        mockMvc.perform(get("/api/v1/payment-links/{id}/open", link.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Payment link has an authorized payment awaiting capture")));

        verify(acquiringClient, never()).createEcomOrder(any(), anyString(), anyString(), any(), anyString());
    }

    // --- P1-7: рассчитанный платёж должен считаться один раз --------------------------------

    // Регресс-тест P1-7. refreshStatus считал только что рассчитанный платёж дважды — Hibernate
    // автофлашит смену статуса перед запросом подсчёта, а код прибавлял ещё единицу, — и ссылка на
    // два платежа закрывалась после первого.
    @Test
    void refreshStatus_onMultiUseLink_doesNotCompleteAfterFirstPayment() throws Exception {
        UUID id = createMultiUseLinkAndGetId(2);

        settleOnePaymentThroughProvider(id);

        PaymentLink link = paymentLinkRepository.findById(id).orElseThrow();
        Assertions.assertEquals(PaymentLinkStatus.ACTIVE, link.getStatus());
        Assertions.assertEquals(1, link.getCurrentPaymentsCount());
    }

    // ...а платёж, который действительно упирается в лимит, ссылку по-прежнему закрывает.
    @Test
    void refreshStatus_onMultiUseLink_completesAfterLastPayment() throws Exception {
        UUID id = createMultiUseLinkAndGetId(2);

        settleOnePaymentThroughProvider(id);
        settleOnePaymentThroughProvider(id);

        PaymentLink link = paymentLinkRepository.findById(id).orElseThrow();
        Assertions.assertEquals(PaymentLinkStatus.COMPLETED, link.getStatus());
        Assertions.assertEquals(2, link.getCurrentPaymentsCount());
    }

    // --- P1-9: у каждой ссылки есть срок жизни, и он ограничен ------------------------------

    // Значение по умолчанию из pbl.link.default-ttl. Ссылки создавались с expires_at NULL — ничего
    // никогда не истекало, а портал рисовал поверх них 24-часовой обратный отсчёт.
    @Test
    void createLink_withoutExpiresAt_getsDefaultTtl() throws Exception {
        Instant beforeCreate = Instant.now();

        Instant expiresAt = expiresAtOf(createLinkExpectingSuccess(validCreateRequest()));

        Instant expected = beforeCreate.plus(DEFAULT_TTL);
        Assertions.assertTrue(
                Duration.between(expected, expiresAt).abs().compareTo(Duration.ofSeconds(30)) < 0,
                "expected an expiry around " + expected + " but got " + expiresAt);
    }

    @Test
    void createLink_withExplicitExpiresAt_usesIt() throws Exception {
        Instant chosen = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS);
        ObjectNode request = validCreateRequest();
        request.put("expiresAt", chosen.toString());

        var created = createLinkExpectingSuccess(request);

        Assertions.assertEquals(chosen, expiresAtOf(created));
        PaymentLink stored = paymentLinkRepository.findById(idOf(created)).orElseThrow();
        Assertions.assertEquals(chosen, stored.getExpiresAt());
    }

    @Test
    void createLink_withExpiresAtInThePast_returns400() throws Exception {
        ObjectNode request = validCreateRequest();
        request.put("expiresAt", Instant.now().minus(Duration.ofHours(1)).toString());

        mockMvc.perform(authed(post("/api/v1/payment-links"), headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("expiresAt must be in the future")));

        Assertions.assertTrue(paymentLinkRepository.findAll().isEmpty(), "the refused link must not be persisted");
    }

    // Потолок — это pbl.link.max-ttl, и отказ обязан его назвать.
    @Test
    void createLink_withExpiresAtBeyondMaxTtl_returns400() throws Exception {
        ObjectNode request = validCreateRequest();
        request.put("expiresAt", Instant.now().plus(Duration.ofDays(100)).toString());

        mockMvc.perform(authed(post("/api/v1/payment-links"), headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("90 days")));

        Assertions.assertTrue(paymentLinkRepository.findAll().isEmpty(), "the refused link must not be persisted");
    }

    @Test
    void updateLink_withExpiresAtBeyondMaxTtl_returns400() throws Exception {
        UUID id = createLinkAndGetId(headToken);
        Instant before = paymentLinkRepository.findById(id).orElseThrow().getExpiresAt();

        ObjectNode update = objectMapper.createObjectNode();
        update.put("expiresAt", Instant.now().plus(Duration.ofDays(100)).toString());

        mockMvc.perform(authed(patch("/api/v1/payment-links/{id}", id), headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("90 days")));

        Assertions.assertEquals(before, paymentLinkRepository.findById(id).orElseThrow().getExpiresAt());
    }

    // Главный случай для потолка: он отсчитывается от created_at самой ссылки, а не от момента
    // PATCH. От "сейчас" мерчант мог бы продлевать ссылку ещё на 90 дней каждый день, и потолок не
    // ограничивал бы ничего. Ссылка создана 80 дней назад, запаса осталось 10 дней — и ещё 30 дней
    // отвергаются, хотя от сегодня это заметно внутри 90 дней.
    @Test
    void updateLink_onAnOldLink_cannotExtendBeyondMaxTtlFromCreation() throws Exception {
        UUID id = createLinkAndGetId(headToken);
        backdateCreatedAt(id, Duration.ofDays(80));

        ObjectNode tooFar = objectMapper.createObjectNode();
        tooFar.put("expiresAt", Instant.now().plus(Duration.ofDays(30)).toString());
        mockMvc.perform(authed(patch("/api/v1/payment-links/{id}", id), headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooFar)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("90 days")));

        // В пределах остатка тот же PATCH принимается — значит, отказ выше это потолок, а не запрет
        // продлевать старую ссылку вообще.
        Instant withinCeiling = Instant.now().plus(Duration.ofDays(5)).truncatedTo(ChronoUnit.SECONDS);
        ObjectNode allowed = objectMapper.createObjectNode();
        allowed.put("expiresAt", withinCeiling.toString());
        mockMvc.perform(authed(patch("/api/v1/payment-links/{id}", id), headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allowed)))
                .andExpect(status().isOk());

        Assertions.assertEquals(withinCeiling, paymentLinkRepository.findById(id).orElseThrow().getExpiresAt());
    }

    @Test
    void updateLink_withValidExpiresAt_extendsIt() throws Exception {
        UUID id = createLinkAndGetId(headToken);
        Instant extended = Instant.now().plus(Duration.ofDays(10)).truncatedTo(ChronoUnit.SECONDS);

        ObjectNode update = objectMapper.createObjectNode();
        update.put("expiresAt", extended.toString());

        String response = mockMvc.perform(authed(patch("/api/v1/payment-links/{id}", id), headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Assertions.assertEquals(extended, Instant.parse(objectMapper.readTree(response).get("expiresAt").asText()));
        Assertions.assertEquals(extended, paymentLinkRepository.findById(id).orElseThrow().getExpiresAt());
    }

    // Поля не было в PaymentLinkResponse, хотя в PaymentLinkSummaryResponse оно было, — именно это
    // и толкнуло портал выдумать собственный срок жизни.
    @Test
    void createLink_response_containsExpiresAt() throws Exception {
        UUID id = createLinkAndGetId(headToken);

        mockMvc.perform(authed(get("/api/v1/payment-links/{id}", id), headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt", notNullValue()));
    }

    // Теперь, когда ссылки действительно истекают, пути открытия есть что отклонять.
    @Test
    void expiredLink_cannotBeOpened() throws Exception {
        PaymentLink link = linkFixture(UsageType.SINGLE, null, Instant.now().minus(Duration.ofMinutes(1)));

        mockMvc.perform(get("/api/v1/payment-links/{id}/open", link.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Payment link has expired")));

        verify(acquiringClient, never()).createEcomOrder(any(), anyString(), anyString(), any(), anyString());
    }

    // Свёртка PaymentLinkScheduler ходит раз в пять минут. Она была верна и P1-9 её не трогал —
    // просто находить было нечего, пока все ссылки создавались без срока.
    @Test
    void scheduler_marksExpiredLinksAsExpired() {
        PaymentLink overdue = linkFixture(UsageType.SINGLE, null, Instant.now().minus(Duration.ofMinutes(1)));
        PaymentLink live = linkFixture(UsageType.SINGLE, null, Instant.now().plus(DEFAULT_TTL));

        Integer expired = new TransactionTemplate(transactionManager)
                .execute(status -> paymentLinkRepository.expireActiveLinksBefore(Instant.now()));

        Assertions.assertEquals(1, expired);
        Assertions.assertEquals(PaymentLinkStatus.EXPIRED,
                paymentLinkRepository.findById(overdue.getId()).orElseThrow().getStatus());
        Assertions.assertEquals(PaymentLinkStatus.ACTIVE,
                paymentLinkRepository.findById(live.getId()).orElseThrow().getStatus());
    }

    // Создаёт ссылку через API и возвращает разобранное тело ответа.
    private JsonNode createLinkExpectingSuccess(ObjectNode request) throws Exception {
        String response = mockMvc.perform(authed(post("/api/v1/payment-links"), headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private static Instant expiresAtOf(JsonNode linkResponse) {
        return Instant.parse(linkResponse.get("expiresAt").asText());
    }

    private static UUID idOf(JsonNode linkResponse) {
        return UUID.fromString(linkResponse.get("id").asText());
    }

    // Сдвигает created_at ссылки в прошлое. Это колонка @CreationTimestamp с updatable = false,
    // поэтому обычным save её не подвинуть.
    private void backdateCreatedAt(UUID linkId, Duration age) {
        int rows = jdbcTemplate.update(
                "UPDATE payment_links SET created_at = TIMESTAMPADD(SECOND, ?, created_at) WHERE id = ?",
                -age.toSeconds(), linkId);
        Assertions.assertEquals(1, rows, "backdating helper must touch exactly one row");
    }

    // Открывает ссылку и один раз опрашивает статус; стаб провайдера отвечает FullyPaid.
    private void settleOnePaymentThroughProvider(UUID linkId) throws Exception {
        mockMvc.perform(get("/api/v1/payment-links/{id}/open", linkId))
                .andExpect(status().isFound());

        Transaction attempt = transactionRepository.findByLinkIdOrderByCreatedAtDesc(linkId).getFirst();
        mockMvc.perform(authed(get("/api/v1/transactions/{identifier}/status", attempt.getProviderOrderId()), headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")));
    }

    private UUID createMultiUseLinkAndGetId(int maxPayments) throws Exception {
        ObjectNode request = validCreateRequest();
        request.put("merchantOrderId", "ORDER-MULTI-" + maxPayments);
        request.put("maxPayments", maxPayments);

        String response = mockMvc.perform(authed(post("/api/v1/payment-links"), headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    // Фикстура ссылки с явной политикой использования, минуя API.
    private PaymentLink linkFixture(UsageType usageType, Integer maxPayments) {
        return linkFixture(usageType, maxPayments, null);
    }

    // Та же фикстура со своим сроком жизни — прошедший expiresAt даёт просроченную ссылку.
    private PaymentLink linkFixture(UsageType usageType, Integer maxPayments, Instant expiresAt) {
        return paymentLinkRepository.save(PaymentLink.builder()
                .expiresAt(expiresAt)
                .providerReference("RID-REOPEN")
                .merchantOrderId("ORDER-REOPEN")
                .terminalId(TERMINAL_ID)
                .amount(new BigDecimal("100.00"))
                .currency("AZN")
                .description("Reopen fixture")
                .paymentType(PaymentType.DMS)
                .usageType(usageType)
                .maxPayments(maxPayments)
                .currentPaymentsCount(0)
                .status(PaymentLinkStatus.ACTIVE)
                .build());
    }

    // Та же фикстура, припаркованная в заданном статусе, для таблицы переходов правки (P2-9).
    // Статус ставится после вставки: путь билдера всегда стартует со ссылки ACTIVE, как и create.
    private PaymentLink linkFixtureInStatus(PaymentLinkStatus status, Instant expiresAt) {
        PaymentLink link = linkFixture(UsageType.MULTIPLE, 5, expiresAt);
        link.setStatus(status);
        return paymentLinkRepository.save(link);
    }

    // Попытка оплаты на фикстуре ссылки, в том состоянии, которое нужно сценарию.
    private Transaction attemptFixture(PaymentLink link, TransactionStatus status) {
        return transactionRepository.save(Transaction.builder()
                .link(link)
                .merchantRid(UUID.randomUUID())
                .providerOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .providerPassword("provider-password")
                .amount(link.getAmount())
                .refundedAmount(BigDecimal.ZERO)
                .status(status)
                .build());
    }

    // Пара ссылка + транзакция напрямую, чтобы тесты листингов не зависели от стаба эквайринга.
    private Transaction createTransaction(int terminalId, String merchantOrderId) {
        return createTransaction(terminalId, merchantOrderId, TransactionStatus.SUCCESS);
    }

    private Transaction createTransaction(int terminalId, String merchantOrderId, TransactionStatus status) {
        PaymentLink link = paymentLinkRepository.save(PaymentLink.builder()
                .providerReference("RID-" + merchantOrderId)
                .merchantOrderId(merchantOrderId)
                .terminalId(terminalId)
                .amount(new BigDecimal("100.00"))
                .currency("AZN")
                .description("Fixture for " + merchantOrderId)
                .paymentType(PaymentType.SMS)
                .usageType(UsageType.SINGLE)
                .currentPaymentsCount(0)
                .status(PaymentLinkStatus.ACTIVE)
                .build());

        return transactionRepository.save(Transaction.builder()
                .link(link)
                .merchantRid(UUID.randomUUID())
                .providerOrderId("ORD-" + merchantOrderId)
                .providerPassword("provider-password")
                .amount(new BigDecimal("100.00"))
                .refundedAmount(BigDecimal.ZERO)
                .status(status)
                .build());
    }

    // Объект order из §5.8.6 — оплаченный SMS-заказ, прочитанный со всеми тремя уровнями детализации,
    // как его запрашивает TxpgAcquiringClient.getOrderStatus: trans[] с записью покупки, srcToken с
    // маскированной картой и password, который несёт реальный payload (P0-9 срезает его перед
    // сохранением). Значения — из контракта.
    private static Map<String, Object> contractOrderPayload() {
        Map<String, Object> purchase = new LinkedHashMap<>();
        purchase.put("approvalCode", "629677");
        purchase.put("actionId", "230314-06303941-0024iz=");
        purchase.put("orderId", 11338);
        purchase.put("terminalId", 1);
        purchase.put("merchantId", 1);
        purchase.put("billingStatus", "Normal");
        purchase.put("isReversal", false);
        purchase.put("ridByAcquirer", "230314000000002720");
        purchase.put("ridByPmo", "230314000000002720");
        purchase.put("regTime", "2023-03-14 10:30:39");
        purchase.put("clearDay", "2020-06-18");
        purchase.put("clearAmount", 5);
        purchase.put("clearCcy", "AZN");
        purchase.put("amount", 5);
        purchase.put("rrn", "629677123123123123");
        purchase.put("currency", "AZN");
        purchase.put("description", "Purchase");
        purchase.put("phase", "Single");
        purchase.put("type", "Purchase");

        Map<String, Object> srcToken = new LinkedHashMap<>();
        srcToken.put("id", 9216);
        srcToken.put("paymentMethod", "Card");
        srcToken.put("role", "Src");
        srcToken.put("status", "Active");
        srcToken.put("regTime", "2023-03-14 10:31:30");
        srcToken.put("entryMode", "ECommerce");
        srcToken.put("displayName", "426863******3689");
        srcToken.put("owner", Map.of());
        srcToken.put("card", Map.of(
                "authentication", Map.of("needCvv2", false, "needTds", false),
                "expiration", "0131",
                "brand", "Visa",
                "restoredFromId", 9217,
                "issuerRid", "4268"));

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", 11338);
        order.put("hppUrl", "https://test.millikart.az:8004");
        order.put("password", "1h1pq153fk8xk");
        order.put("status", "FullyPaid");
        order.put("ridByMerchant", "123123871283618376123");
        order.put("prevStatus", "Preparing");
        order.put("lastStatusLogin", "Admin");
        order.put("amount", 5);
        order.put("currency", "AZN");
        order.put("createTime", "2023-03-14 10:31:23");
        order.put("storedTokens", List.of(Map.of("id", 9217)));
        order.put("trans", List.of(purchase));
        order.put("cvv2AuthStatus", "Provided");
        order.put("authorizedChargeAmount", 5);
        order.put("clearedChargeAmount", 5);
        order.put("clearedRefundAmount", 0);
        order.put("description", " test order");
        order.put("language", "en");
        order.put("srcToken", srcToken);
        order.put("custAttrs", List.of(
                Map.of("rid", "PrevStatus", "valAsStr", "Preparing"),
                Map.of("rid", "PmoResultCode", "valAsStr", "Approved")));
        return order;
    }

    private UUID createLinkAndGetId(String token) throws Exception {
        String response = mockMvc.perform(authed(post("/api/v1/payment-links"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
