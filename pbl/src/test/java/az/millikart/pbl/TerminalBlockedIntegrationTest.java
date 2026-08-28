package az.millikart.pbl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.security.JwtProvider;
import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.domain.TerminalStatus;
import az.millikart.pbl.domain.Transaction;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.pbl.domain.UsageType;
import az.millikart.pbl.provider.AcquiringClient;
import az.millikart.pbl.provider.dto.MoneyOperationResult;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRepository;
import az.millikart.pbl.service.OpenLinkService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// P2-8 глазами pbl: заблокированный терминал не принимает новых платежей, а всё уже начатое
// продолжает работать (Р-38). Вторая половина — суть решения и то, что легче всего сломать
// «наведением порядка»: проверка блокировки в capture, refund или опросе статуса заперла бы
// деньги плательщика на карте, задержала возврат или оставила платёж PENDING навсегда.
@SpringBootTest
@AutoConfigureMockMvc
class TerminalBlockedIntegrationTest {

    private static final int BLOCKED_TERMINAL = 820001;
    private static final int ACTIVE_TERMINAL = 820002;
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private OpenLinkService openLinkService;

    @Autowired
    private PaymentLinkRepository paymentLinkRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TerminalRepository terminalRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private AcquiringClient acquiringClient;

    private String headToken;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        paymentLinkRepository.deleteAll();
        terminalRepository.deleteAll();

        terminalRepository.save(terminal(BLOCKED_TERMINAL, TerminalStatus.BLOCKED));
        terminalRepository.save(terminal(ACTIVE_TERMINAL, TerminalStatus.ACTIVE));

        headToken = "Bearer " + jwtProvider.generateToken(
                "head-user", "head-user@test.com", "COMPANY_HEAD", "test-company");
    }

    // 11. Плательщик не может открыть приостановленную ссылку, эквайер о ней не узнаёт

    @Test
    void openingSuspendedLink_isRefused_andNoOrderIsRegistered() {
        UUID linkId = link(BLOCKED_TERMINAL, PaymentLinkStatus.SUSPENDED).getId();

        assertThatThrownBy(() -> openLinkService.openAndBuildRedirect(linkId, "203.0.113.9", "curl"))
                .isInstanceOf(InvalidStateException.class)
                // Плательщику сообщают, что ссылка недоступна, и ничего про терминал.
                .hasMessageNotContainingAny("terminal", "BLOCKED", String.valueOf(BLOCKED_TERMINAL));

        verify(acquiringClient, never()).createEcomOrder(any(), anyString(), anyString(), any(), anyString());
        assertThat(transactionRepository.count()).isZero();
    }

    // Ссылка, оставшаяся ACTIVE на заблокированном терминале (след неудавшейся или частичной
    // блокировки), всё равно получает отказ: терминал проверяется сам по себе, а не через веру
    // в то, что все ссылки уже приостановлены.
    @Test
    void openingActiveLinkOnBlockedTerminal_isRefused() {
        UUID linkId = link(BLOCKED_TERMINAL, PaymentLinkStatus.ACTIVE).getId();

        assertThatThrownBy(() -> openLinkService.openAndBuildRedirect(linkId, "203.0.113.9", "curl"))
                .isInstanceOf(InvalidStateException.class);

        verify(acquiringClient, never()).createEcomOrder(any(), anyString(), anyString(), any(), anyString());
    }

    // 12. Блокировка приходит, пока открытие стоит в очереди за замком ссылки

    // Почему терминал читается после захвата замка на строке ссылки: чужая транзакция держит
    // замок, открытие встаёт в очередь (проверка до замка увидела бы терминал ещё ACTIVE),
    // терминал блокируют и замок отдают — открытие обязано отказать. Перенеси проверку выше
    // lockLinkOrThrow, и здесь зарегистрируется заказ на выведенном из строя терминале.
    @Test
    void terminalBlockedWhileTheOpenWaitsForTheLock_stopsThePayment() throws Exception {
        // Ссылка и терминал изначально вполне платёжеспособны.
        terminalRepository.save(terminal(ACTIVE_TERMINAL, TerminalStatus.ACTIVE));
        UUID linkId = link(ACTIVE_TERMINAL, PaymentLinkStatus.ACTIVE).getId();

        CountDownLatch lockHeld = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .execute(status -> {
                        paymentLinkRepository.findWithLockById(linkId);
                        lockHeld.countDown();
                        // Достаточно, чтобы открытие точно встало в очередь за замком,
                        // а для любой более ранней проверки терминал был ещё ACTIVE.
                        sleep(250);
                        terminalRepository.save(terminal(ACTIVE_TERMINAL, TerminalStatus.BLOCKED));
                        return null;
                    }));

            assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> openLinkService.openAndBuildRedirect(linkId, "203.0.113.9", "curl"))
                    .as("the open must see the block that landed while it waited for the lock")
                    .isInstanceOf(InvalidStateException.class);

            holder.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        verify(acquiringClient, never()).createEcomOrder(any(), anyString(), anyString(), any(), anyString());
        assertThat(transactionRepository.count())
                .as("no payment attempt may exist for a terminal blocked before the lock was granted")
                .isZero();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // 13. На заблокированном терминале нельзя создать новую ссылку

    @Test
    void creatingLinkOnBlockedTerminal_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/payment-links")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createLinkBody(BLOCKED_TERMINAL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("blocked")));

        assertThat(paymentLinkRepository.count()).isZero();
    }

    @Test
    void creatingLinkOnActiveTerminal_stillWorks() throws Exception {
        mockMvc.perform(post("/api/v1/payment-links")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createLinkBody(ACTIVE_TERMINAL)))
                .andExpect(status().isCreated());
    }

    // 15-16. SUSPENDED мерчант не ставит и не снимает

    @Test
    void merchantCannotSuspendALinkByHand() throws Exception {
        PaymentLink link = link(ACTIVE_TERMINAL, PaymentLinkStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/payment-links/" + link.getId())
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("blocking terminal")));

        assertThat(paymentLinkRepository.findById(link.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentLinkStatus.ACTIVE);
    }

    @Test
    void merchantCannotReactivateASuspendedLink() throws Exception {
        PaymentLink link = link(BLOCKED_TERMINAL, PaymentLinkStatus.SUSPENDED);

        mockMvc.perform(patch("/api/v1/payment-links/" + link.getId())
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                // Сообщение обязано указывать на терминал: сама ссылка ни при чём.
                .andExpect(jsonPath("$.message", containsString("terminal")));

        assertThat(paymentLinkRepository.findById(link.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentLinkStatus.SUSPENDED);
    }

    @Test
    void merchantCannotCancelASuspendedLinkEither() throws Exception {
        PaymentLink link = link(BLOCKED_TERMINAL, PaymentLinkStatus.SUSPENDED);

        mockMvc.perform(patch("/api/v1/payment-links/" + link.getId())
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELED\"}"))
                .andExpect(status().isBadRequest());
    }

    // Приостановленная ссылка остаётся ссылкой мерчанта: видна вместе со своим статусом.
    @Test
    void suspendedLinksRemainVisibleToTheMerchant() throws Exception {
        link(BLOCKED_TERMINAL, PaymentLinkStatus.SUSPENDED);

        mockMvc.perform(get("/api/v1/payment-links")
                        .param("status", "SUSPENDED")
                        .header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].status", is("SUSPENDED")));
    }

    // 17-19. Чего блокировка трогать НЕ должна (Р-38)

    // Ключевая проверка Р-38: возврат отдаёт деньги за уже случившийся платёж, и выведенный из
    // строя терминал не должен держать их в заложниках.
    @Test
    void refundOnBlockedTerminal_goesThrough() throws Exception {
        Transaction paid = transaction(BLOCKED_TERMINAL, TransactionStatus.SUCCESS, PaymentType.SMS, null);
        when(acquiringClient.refund(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new MoneyOperationResult("REF-1", "RRN-1", "APPR-1", Map.of("status", "ok")));

        mockMvc.perform(post("/api/v1/transactions/" + paid.getId() + "/refund")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isOk());

        assertThat(transactionRepository.findById(paid.getId()).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.REFUNDED);
    }

    // Вторая половина Р-38: холд, взятый до блокировки, обязан оставаться захватываемым. Отказ
    // заморозил бы деньги держателя карты до тех пор, пока их не отпустит эмитент: Void у нас нет.
    @Test
    void captureOnBlockedTerminal_goesThrough() throws Exception {
        Transaction held = transaction(BLOCKED_TERMINAL, TransactionStatus.AUTHORIZED, PaymentType.DMS, null);
        when(acquiringClient.completeDms(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new MoneyOperationResult("CAP-1", "RRN-2", "APPR-2", Map.of("status", "ok")));

        mockMvc.perform(post("/api/v1/transactions/" + held.getId() + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isOk());

        assertThat(transactionRepository.findById(held.getId()).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.SUCCESS);
    }

    // И третье: платёж, уже ушедший к эквайеру к моменту блокировки, обязан дойти до финального
    // статуса. Ответ за него никто больше не заберёт — строка PENDING на заблокированном
    // терминале осталась бы PENDING навсегда.
    @Test
    void statusPollOnBlockedTerminal_reachesAFinalStatus() throws Exception {
        Transaction pending = transaction(BLOCKED_TERMINAL, TransactionStatus.PENDING, PaymentType.SMS, "ORD-POLL");
        when(acquiringClient.getOrderStatus(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("status", "FullyPaid"));

        mockMvc.perform(get("/api/v1/transactions/" + pending.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")));

        assertThat(transactionRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.SUCCESS);
    }

    // Фикстуры

    private static Terminal terminal(int id, TerminalStatus status) {
        return Terminal.builder()
                .id(id)
                .name("Terminal " + id)
                .login("TerminalSys/Admin")
                .password("1234")
                .companyId("test-company")
                .status(status)
                .build();
    }

    private PaymentLink link(int terminalId, PaymentLinkStatus status) {
        String key = UUID.randomUUID().toString().substring(0, 8);
        return paymentLinkRepository.save(PaymentLink.builder()
                .providerReference("RID-" + key)
                .merchantOrderId("order-" + key)
                .terminalId(terminalId)
                .amount(AMOUNT)
                .currency("AZN")
                .description("Fixture " + key)
                .paymentType(PaymentType.SMS)
                .usageType(UsageType.SINGLE)
                .currentPaymentsCount(0)
                .status(status)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build());
    }

    private Transaction transaction(int terminalId, TransactionStatus status,
                                    PaymentType paymentType, String providerOrderId) {
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
                .status(PaymentLinkStatus.SUSPENDED)
                .build());

        return transactionRepository.save(Transaction.builder()
                .link(link)
                .merchantRid(UUID.randomUUID())
                .providerOrderId(providerOrderId != null ? providerOrderId : "ORD-" + key)
                .providerPassword("provider-password")
                .amount(AMOUNT)
                .capturedAmount(null)
                .refundedAmount(BigDecimal.ZERO)
                .status(status)
                .build());
    }

    private String createLinkBody(int terminalId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "merchantOrderId", "order-" + UUID.randomUUID().toString().substring(0, 8),
                "terminal", terminalId,
                "amount", AMOUNT,
                "currency", "AZN",
                "description", "New link",
                "paymentType", "SMS",
                "usageType", "SINGLE"));
    }
}
