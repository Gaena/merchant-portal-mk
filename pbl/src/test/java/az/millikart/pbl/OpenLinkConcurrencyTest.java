package az.millikart.pbl;

import az.millikart.common.exception.BusinessException;
import az.millikart.common.security.CredentialCipher;
import az.millikart.common.security.UserPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import az.millikart.common.testing.PostgresIntegrationTest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.domain.Transaction;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.pbl.domain.UsageType;
import az.millikart.txpg.AcquiringClient;
import az.millikart.txpg.dto.EcomCreateOrderResponse;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRepository;
import az.millikart.pbl.dto.UpdatePaymentLinkRequest;
import az.millikart.pbl.service.OpenLinkService;
import az.millikart.pbl.service.PaymentLinkService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.PessimisticLockingFailureException;

// P1-5: два одновременных открытия одной ссылки обязаны дать одну попытку платежа, а не две.
// Старый openAndBuildRedirect проверял ссылку в одной транзакции, звал эквайера вне транзакций
// и вставлял попытку в третьей: оба вызова вместе проходили проверку «ещё не оплачено», оба
// регистрировали заказ, и одноразовая ссылка получала два живых заказа — платили по ней дважды.
// На настоящей PostgreSQL, а не на H2, и это здесь главное: тест держит ссылку под
// `SELECT ... FOR UPDATE`, а семантика блокировок у H2 своя. На эмуляции он подтверждал бы,
// что код не виснет, но не что гонка закрыта той СУБД, которая стоит в проде.
@PostgresIntegrationTest
class OpenLinkConcurrencyTest {

    private static final int TERMINAL_ID = 123456789;
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    // Ответ провайдера намеренно тормозит: окно гонки шире, и сломанная реализация проигрывает его
    // каждый раз, а не раз в сотню прогонов. Каждый вызов мока отдаёт свой providerOrderId, чтобы
    // вторая регистрация не спряталась за идентификаторами первой.
    private static final long PROVIDER_DELAY_MILLIS = 200;

    @Autowired
    private OpenLinkService openLinkService;

    @Autowired
    private PaymentLinkService paymentLinkService;

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

    @MockBean
    private AcquiringClient acquiringClient;

    private final AtomicLong orderIds = new AtomicLong(7000);

    @BeforeEach
    void setUp() {
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

        when(acquiringClient.createEcomOrder(any(), any(), any(), any(), anyString()))
                .thenAnswer(invocation -> {
                    Thread.sleep(PROVIDER_DELAY_MILLIS);
                    long orderId = orderIds.incrementAndGet();
                    return new EcomCreateOrderResponse(new EcomCreateOrderResponse.Order(
                            "https://gateway.txpg.example.com/pay",
                            orderId,
                            "Preparing",
                            "password-" + orderId));
                });
    }

    // Несущий тест P1-5: два потока открывают одну одноразовую ссылку в один момент; до эквайера
    // имеет право дойти ровно один, и в базе обязана остаться ровно одна попытка. На старой
    // четырёхфазной реализации проходили оба вызова и появлялись две строки.
    @Test
    void concurrentOpens_ofSingleUseLink_createExactlyOneTransaction() throws Exception {
        UUID linkId = link(UsageType.SINGLE, null);

        List<Outcome> outcomes = openConcurrently(linkId, 2);

        Assertions.assertEquals(1, outcomes.stream().filter(Outcome::succeeded).count(),
                "exactly one of the two simultaneous opens may be served: " + outcomes);
        Assertions.assertEquals(1, transactionRepository.count(),
                "a one-time link must not end up with two payment attempts");
        verify(acquiringClient, times(1)).createEcomOrder(any(), any(), any(), any(), anyString());

        List<Transaction> attempts = transactionRepository.findByLinkIdOrderByCreatedAtDesc(linkId);
        Assertions.assertEquals(TransactionStatus.PENDING, attempts.getFirst().getStatus());
    }

    // Та же гарантия для многоразовой ссылки: лимит в один платёж остаётся лимитом в один платёж,
    // сколько бы человек ни нажали одновременно.
    @Test
    void concurrentOpens_ofMultiUseLink_respectMaxPayments() throws Exception {
        UUID linkId = link(UsageType.MULTIPLE, 1);

        List<Outcome> outcomes = openConcurrently(linkId, 2);

        Assertions.assertEquals(1, outcomes.stream().filter(Outcome::succeeded).count(),
                "a multi-use link with maxPayments=1 may serve one of two simultaneous opens: " + outcomes);
        Assertions.assertEquals(1, transactionRepository.count());
        verify(acquiringClient, times(1)).createEcomOrder(any(), any(), any(), any(), anyString());
    }

    // LINK-PATCH-LOCK: PATCH читал ссылку без замка и не видел попытку, которую открытие ещё не закоммитило:
    // сумма менялась, пока открытие заводило у эквайера заказ по старой (обход Р-31). Теперь PATCH берёт тот
    // же замок с NOWAIT и получает отказ сразу, а после открытия сумма уже заморожена.
    @Test
    void patchWhileAnOpenIsAtTheAcquirer_isRefused_andTheAmountStaysFrozen() throws Exception {
        UUID linkId = link(UsageType.SINGLE, null);
        CountDownLatch atAcquirer = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(acquiringClient.createEcomOrder(any(), any(), any(), any(), anyString()))
                .thenAnswer(invocation -> {
                    atAcquirer.countDown();
                    release.await(10, TimeUnit.SECONDS);
                    return new EcomCreateOrderResponse(new EcomCreateOrderResponse.Order(
                            "https://gateway.txpg.example.com/pay", 7001L, "Preparing", "password-7001"));
                });
        UserPrincipal head = new UserPrincipal("head", "head@test.com", "COMPANY_HEAD", "test-company");
        UpdatePaymentLinkRequest newAmount =
                new UpdatePaymentLinkRequest(new BigDecimal("1.00"), null, null, null, null, null, null);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> open = pool.submit(() -> openLinkService.openAndBuildRedirect(linkId, "10.0.0.1", "junit"));
            Assertions.assertTrue(atAcquirer.await(10, TimeUnit.SECONDS), "the open never reached the acquirer");

            Assertions.assertThrows(PessimisticLockingFailureException.class,
                    () -> paymentLinkService.update(linkId, newAmount, head));

            release.countDown();
            open.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }

        Assertions.assertThrows(BusinessException.class, () -> paymentLinkService.update(linkId, newAmount, head));
        Assertions.assertEquals(0, AMOUNT.compareTo(paymentLinkRepository.findById(linkId).orElseThrow().getAmount()));
    }

    // Пускает открытия одной ссылки по общему стартовому выстрелу, чтобы они оказались внутри
    // openAndBuildRedirect вместе, а не друг за другом.
    private List<Outcome> openConcurrently(UUID linkId, int threads) throws Exception {
        CountDownLatch startingGun = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String clientIp = "10.0.0." + (i + 1);
                futures.add(pool.submit(() -> {
                    startingGun.await();
                    try {
                        return new Outcome(openLinkService.openAndBuildRedirect(linkId, clientIp, "junit"), null);
                    } catch (RuntimeException refused) {
                        return new Outcome(null, refused);
                    }
                }));
            }

            startingGun.countDown();

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private UUID link(UsageType usageType, Integer maxPayments) {
        PaymentLink link = paymentLinkRepository.save(PaymentLink.builder()
                .providerReference("RID-CONCURRENT")
                .merchantOrderId("ORDER-CONCURRENT")
                .terminalId(TERMINAL_ID)
                .amount(AMOUNT)
                .currency("AZN")
                .description("Concurrency fixture")
                .paymentType(PaymentType.SMS)
                .usageType(usageType)
                .maxPayments(maxPayments)
                .currentPaymentsCount(0)
                .status(PaymentLinkStatus.ACTIVE)
                .build());
        return link.getId();
    }

    // Либо URL редиректа, который дало открытие, либо отказ, который оно получило.
    private record Outcome(String redirectUrl, RuntimeException refusal) {

        boolean succeeded() {
            return redirectUrl != null;
        }

        @Override
        public String toString() {
            return succeeded() ? "served(" + redirectUrl + ")" : "refused(" + refusal + ")";
        }
    }
}
