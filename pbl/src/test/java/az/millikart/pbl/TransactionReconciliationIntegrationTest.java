package az.millikart.pbl;

import az.millikart.common.security.CredentialCipher;
import az.millikart.common.testing.PostgresIntegrationTest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import az.millikart.common.exception.BusinessException;
import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.domain.Transaction;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.pbl.domain.UsageType;
import az.millikart.pbl.provider.AcquiringClient;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRepository;
import az.millikart.pbl.service.PaymentLinkService;
import az.millikart.pbl.service.TransactionReconciliationService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

// P1-3: фоновый прогон, который закрывает транзакции, за которыми плательщик не вернулся.
// StubAcquiringClient всегда отвечает FullyPaid — единственный ответ, на котором эти случаи
// нельзя запирать, поэтому провайдер заменён моком и каждый тест диктует ответ эквайера сам.
// Прогон вызывается напрямую: pbl.reconciliation.enabled в тестовом профиле false, cron не бьёт.
//
// На настоящей PostgreSQL, а не на H2: метки, по которым свёртка узнаёт судьбу платежа, лежат
// в `provider_response` — колонке jsonb, а выборка идёт по возрасту операции. И тип, и работа
// с временем у эмуляции свои.
@PostgresIntegrationTest
class TransactionReconciliationIntegrationTest {

    // Обязаны повторять pbl.reconciliation.* тестового yaml: фикстуры состариваются относительно них.
    private static final Duration MIN_AGE = Duration.ofMinutes(2);
    private static final Duration MAX_AGE = Duration.ofHours(24);
    private static final Duration GIVE_UP_AGE = Duration.ofDays(7);
    private static final int BATCH_SIZE = 3;

    private static final int TERMINAL_ID = 123456789;

    @Autowired
    private TransactionReconciliationService reconciliationService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PaymentLinkRepository paymentLinkRepository;

    @Autowired
    private TerminalRepository terminalRepository;

    @Autowired
    private CredentialCipher credentialCipher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AcquiringClient acquiringClient;

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
    }

    @Test
    void reconcile_pendingOlderThanMinAge_providerSaysPaid_becomesSuccess() {
        Transaction tx = agedTransaction("PAID", TransactionStatus.PENDING, Duration.ofMinutes(10));
        providerAnswers("FullyPaid");

        Assertions.assertEquals(1, reconciliationService.reconcilePendingTransactions());

        Assertions.assertEquals(TransactionStatus.SUCCESS, statusOf(tx));
        // Одноразовая ссылка израсходована, как только платёж закрылся.
        Assertions.assertEquals(PaymentLinkStatus.COMPLETED, linkStatusOf(tx));
    }

    @Test
    void reconcile_pendingYoungerThanMinAge_isNotTouched() {
        // Без состаривания: страницу возврата только что опросили, прогон не должен вмешиваться.
        Transaction fresh = createTransaction("FRESH", TransactionStatus.PENDING);
        providerAnswers("FullyPaid");

        Assertions.assertEquals(0, reconciliationService.reconcilePendingTransactions());

        verify(acquiringClient, never()).getOrderStatus(anyString(), anyString(), any());
        Assertions.assertEquals(TransactionStatus.PENDING, statusOf(fresh));
    }

    @Test
    void reconcile_pendingOlderThanMaxAge_providerSaysPreparing_becomesFailed() {
        // Preparing = заказ у эквайера есть, но карту так и не ввели.
        Transaction tx = agedTransaction("ABANDONED", TransactionStatus.PENDING, MAX_AGE.plusHours(1));
        providerAnswers("Preparing");

        Assertions.assertEquals(1, reconciliationService.reconcilePendingTransactions());

        Assertions.assertEquals(TransactionStatus.FAILED, statusOf(tx));

        Map<String, Object> providerResponse = reload(tx).getProviderResponse();
        Assertions.assertNotNull(providerResponse, "the acquirer payload must be kept alongside the marker");
        Assertions.assertEquals("ABANDONED_TIMEOUT", providerResponse.get("reconciliationOutcome"));
        Assertions.assertNotNull(providerResponse.get("reconciledAt"));
        // Последний payload провайдера слит, а не затёрт.
        Assertions.assertEquals("Preparing", providerResponse.get("status"));
        // Одноразовая ссылка остаётся ACTIVE, чтобы клиент мог попробовать снова.
        Assertions.assertEquals(PaymentLinkStatus.ACTIVE, linkStatusOf(tx));
    }

    // По max-age гасится только старое: Preparing моложе суток — заказ ещё можно оплатить, строка
    // остаётся PENDING. Ловит сравнение возраста с min-age вместо max-age: сверка гасила бы живые оплаты.
    @Test
    void reconcile_pendingYoungerThanMaxAge_providerSaysPreparing_staysPending() {
        Transaction tx = agedTransaction("STILL-OPEN", TransactionStatus.PENDING, MAX_AGE.minusHours(1));
        providerAnswers("Preparing");

        Assertions.assertEquals(1, reconciliationService.reconcilePendingTransactions());

        Assertions.assertEquals(TransactionStatus.PENDING, statusOf(tx));
        Map<String, Object> providerResponse = reload(tx).getProviderResponse();
        Assertions.assertTrue(providerResponse == null
                        || !"ABANDONED_TIMEOUT".equals(providerResponse.get("reconciliationOutcome")),
                "a young unpaid order must not be timed out: " + providerResponse);
        Assertions.assertEquals(PaymentLinkStatus.ACTIVE, linkStatusOf(tx));
    }

    // Несущий тест P1-3: недоступный шлюз нельзя читать как «платёж не прошёл». Иначе суточная
    // авария разом провалила бы транзакции, которые на самом деле оплачены.
    @Test
    void reconcile_pendingOlderThanMaxAge_providerUnreachable_staysPending() {
        Transaction tx = agedTransaction("UNREACHABLE", TransactionStatus.PENDING, MAX_AGE.plusHours(1));
        when(acquiringClient.getOrderStatus(anyString(), anyString(), any()))
                .thenThrow(new BusinessException("Order status check failed: connection refused"));

        Assertions.assertEquals(1, reconciliationService.reconcilePendingTransactions());

        Transaction untouched = reload(tx);
        Assertions.assertEquals(TransactionStatus.PENDING, untouched.getStatus());
        Assertions.assertNull(untouched.getProviderResponse(),
                "a failed poll must not stamp a reconciliation outcome");
    }

    @Test
    void reconcile_authorizedTransaction_isNotTouched() {
        // DMS-холд в ожидании capture — состояние покоя, а не застревание (P1-6).
        Transaction authorized = agedTransaction("HOLD", TransactionStatus.AUTHORIZED, MAX_AGE.plusHours(24));
        providerAnswers("FullyPaid");

        Assertions.assertEquals(0, reconciliationService.reconcilePendingTransactions());

        verify(acquiringClient, never()).getOrderStatus(anyString(), anyString(), any());
        Assertions.assertEquals(TransactionStatus.AUTHORIZED, statusOf(authorized));
    }

    @Test
    void reconcile_terminalStatuses_areNotPickedUp() {
        Transaction success = agedTransaction("DONE", TransactionStatus.SUCCESS, Duration.ofHours(48));
        Transaction failed = agedTransaction("DEAD", TransactionStatus.FAILED, Duration.ofHours(48));
        Transaction refunded = agedTransaction("BACK", TransactionStatus.REFUNDED, Duration.ofHours(48));
        providerAnswers("FullyPaid");

        Assertions.assertEquals(0, reconciliationService.reconcilePendingTransactions());

        verify(acquiringClient, never()).getOrderStatus(anyString(), anyString(), any());
        Assertions.assertEquals(TransactionStatus.SUCCESS, statusOf(success));
        Assertions.assertEquals(TransactionStatus.FAILED, statusOf(failed));
        Assertions.assertEquals(TransactionStatus.REFUNDED, statusOf(refunded));
    }

    @Test
    void reconcile_respectsBatchSize() {
        int overflow = BATCH_SIZE + 2;
        for (int i = 0; i < overflow; i++) {
            agedTransaction("BULK-" + i, TransactionStatus.PENDING, Duration.ofMinutes(10 + i));
        }
        providerAnswers("FullyPaid");

        Assertions.assertEquals(BATCH_SIZE, reconciliationService.reconcilePendingTransactions());

        Assertions.assertEquals(BATCH_SIZE,
                transactionRepository.findAll().stream()
                        .filter(t -> t.getStatus() == TransactionStatus.SUCCESS)
                        .count());
        Assertions.assertEquals(overflow - BATCH_SIZE,
                transactionRepository.findAll().stream()
                        .filter(t -> t.getStatus() == TransactionStatus.PENDING)
                        .count());
    }

    @Test
    void reconcile_oneFailingTransaction_doesNotAbortBatch() {
        // Сбой эквайера reconcileOne ловит сам; защиту самого прохода проверяет тест ниже.
        // Сначала самые старые, поэтому сломанная заведомо обрабатывается раньше здоровой.
        Transaction broken = agedTransaction("STUCK", TransactionStatus.PENDING, Duration.ofMinutes(30));
        Transaction healthy = agedTransaction("OK", TransactionStatus.PENDING, Duration.ofMinutes(10));

        when(acquiringClient.getOrderStatus(eq("ORD-STUCK"), anyString(), any()))
                .thenThrow(new BusinessException("Order status check failed: connection refused"));
        when(acquiringClient.getOrderStatus(eq("ORD-OK"), anyString(), any()))
                .thenReturn(Map.of("status", "FullyPaid"));

        Assertions.assertEquals(2, reconciliationService.reconcilePendingTransactions());

        Assertions.assertEquals(TransactionStatus.PENDING, statusOf(broken));
        Assertions.assertEquals(TransactionStatus.SUCCESS, statusOf(healthy));
    }

    // Защита прохода в самом сервисе сверки: reconcileOne, бросивший исключение, не обрывает пакет, и
    // следующая операция всё равно сверяется. Ловит снятый try/catch в цикле TransactionReconciliationService.
    @Test
    void reconcile_aTransactionWhoseReconciliationThrows_doesNotAbortTheBatch() {
        Transaction first = agedTransaction("THROWS", TransactionStatus.PENDING, Duration.ofMinutes(30));
        Transaction second = agedTransaction("NEXT", TransactionStatus.PENDING, Duration.ofMinutes(10));
        PaymentLinkService failing = Mockito.mock(PaymentLinkService.class);
        doThrow(new IllegalStateException("the row cannot be reconciled"))
                .when(failing).reconcileOne(eq(first.getId()), any());
        TransactionReconciliationService batch = new TransactionReconciliationService(
                transactionRepository, failing, MIN_AGE, MAX_AGE, GIVE_UP_AGE, 50);

        Assertions.assertEquals(2, batch.reconcilePendingTransactions());

        verify(failing).reconcileOne(eq(first.getId()), any());
        verify(failing).reconcileOne(eq(second.getId()), any());
    }

    // P1-8a: неизвестный или закрытый снаружи статус никогда не становится FAILED

    // Несущий тест P1-8a (Р-20). Раньше незнакомое слово статуса молча проваливалось мимо всех if,
    // строка оставалась PENDING, и прогон по таймауту переводил её в FAILED — оплаченный платёж
    // закрывался как неуспешный. Теперь слово записывают, а строку не трогают, сколько бы ей ни
    // было лет: неизвестный статус не даёт права погасить платёж.
    @Test
    void reconcile_olderThanMaxAge_providerSaysUnknownWord_staysPendingAndIsNotFailed() {
        Transaction tx = agedTransaction("UNKNOWN-WORD", TransactionStatus.PENDING, MAX_AGE.plusHours(1));
        providerAnswers("Paid");

        Assertions.assertEquals(1, reconciliationService.reconcilePendingTransactions());

        Transaction reloaded = reload(tx);
        Assertions.assertEquals(TransactionStatus.PENDING, reloaded.getStatus());
        Map<String, Object> providerResponse = reloaded.getProviderResponse();
        Assertions.assertNotNull(providerResponse);
        Assertions.assertEquals("Paid", providerResponse.get("mpProviderStatus"),
                "the raw word must be kept verbatim so a reviewer sees what the acquirer said");
        Assertions.assertEquals("UNKNOWN", providerResponse.get("mpStatusOutcome"));
        Assertions.assertNull(providerResponse.get("reconciliationOutcome"),
                "an unknown status is not evidence of abandonment — no timeout marker");
        Assertions.assertEquals(PaymentLinkStatus.ACTIVE, linkStatusOf(tx));
    }

    // Сверка спрашивает каждые 2 минуты до 7 дней: WARN о незнакомом статусе — один раз на пару «транзакция,
    // статус», а не на каждый проход. Новое слово той же транзакции — снова WARN.
    @Test
    void reconcile_unknownWord_isWarnedOncePerTransactionAndWord() {
        Logger serviceLogger = (Logger) LoggerFactory.getLogger(PaymentLinkService.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        serviceLogger.addAppender(events);
        try {
            Transaction tx = agedTransaction("UNKNOWN-ONCE", TransactionStatus.PENDING, MAX_AGE.plusHours(1));
            providerAnswers("Paid");
            reconciliationService.reconcilePendingTransactions();
            reconciliationService.reconcilePendingTransactions();
            providerAnswers("Settled");
            reconciliationService.reconcilePendingTransactions();

            List<String> warnings = events.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.contains(tx.getId().toString()))
                    .toList();
            Assertions.assertEquals(2, warnings.size(), warnings.toString());
            Assertions.assertTrue(warnings.get(0).contains("\"Paid\""), warnings.get(0));
            Assertions.assertTrue(warnings.get(1).contains("\"Settled\""), warnings.get(1));
            Assertions.assertEquals(TransactionStatus.PENDING, reload(tx).getStatus());
        } finally {
            serviceLogger.detachAppender(events);
        }
    }

    // Refused = возвращён целиком, PartPaid = частично отменён или возвращён (§5.8.8): деньги
    // двигались вне портала. Это не «брошено плательщиком», поэтому не FAILED — и не REFUNDED,
    // потому что сумма локально неизвестна (её разбор из order.trans[] — отдельная задача,
    // AGENTS.md §10).
    @Test
    void reconcile_olderThanMaxAge_providerSaysRefused_staysPending() {
        Transaction tx = agedTransaction("REFUSED", TransactionStatus.PENDING, MAX_AGE.plusHours(1));
        providerAnswers("Refused");

        Assertions.assertEquals(1, reconciliationService.reconcilePendingTransactions());

        Transaction reloaded = reload(tx);
        Assertions.assertEquals(TransactionStatus.PENDING, reloaded.getStatus());
        Assertions.assertEquals("Refused", reloaded.getProviderResponse().get("mpProviderStatus"));
        Assertions.assertEquals("SETTLED_OTHER", reloaded.getProviderResponse().get("mpStatusOutcome"));
        Assertions.assertNull(reloaded.getProviderResponse().get("reconciliationOutcome"));
    }

    @Test
    void reconcile_olderThanMaxAge_providerSaysPartPaid_staysPending() {
        Transaction tx = agedTransaction("PARTPAID", TransactionStatus.PENDING, MAX_AGE.plusHours(1));
        providerAnswers("PartPaid");

        Assertions.assertEquals(1, reconciliationService.reconcilePendingTransactions());

        Transaction reloaded = reload(tx);
        Assertions.assertEquals(TransactionStatus.PENDING, reloaded.getStatus());
        Assertions.assertEquals("PartPaid", reloaded.getProviderResponse().get("mpProviderStatus"));
        Assertions.assertNull(reloaded.getProviderResponse().get("reconciliationOutcome"));
    }

    // Expired — собственный таймаут эквайера (§5.8.8): финален, даёт FAILED сразу, не по max-age.
    @Test
    void reconcile_youngerThanMaxAge_providerSaysExpired_becomesFailedImmediately() {
        Transaction tx = agedTransaction("EXPIRED", TransactionStatus.PENDING, Duration.ofMinutes(10));
        providerAnswers("Expired");

        Assertions.assertEquals(1, reconciliationService.reconcilePendingTransactions());

        Transaction reloaded = reload(tx);
        Assertions.assertEquals(TransactionStatus.FAILED, reloaded.getStatus());
        // Закончил эквайер, а не наш таймаут: маркера брошенности нет.
        Assertions.assertNull(reloaded.getProviderResponse().get("reconciliationOutcome"));
        Assertions.assertEquals("FAILED_FINAL", reloaded.getProviderResponse().get("mpStatusOutcome"));
    }

    // Cancelled (с двумя l, как в контракте) — полная отмена вне портала: PENDING, а не FAILED.
    @Test
    void reconcile_olderThanMaxAge_providerSaysCancelled_staysPending() {
        Transaction tx = agedTransaction("CANCELLED", TransactionStatus.PENDING, MAX_AGE.plusHours(1));
        providerAnswers("Cancelled");

        Assertions.assertEquals(1, reconciliationService.reconcilePendingTransactions());

        Assertions.assertEquals(TransactionStatus.PENDING, statusOf(tx));
        Assertions.assertEquals("SETTLED_OTHER", reload(tx).getProviderResponse().get("mpStatusOutcome"));
    }

    // Payload без ключа status: UNKNOWN, PENDING, без исключения.
    @Test
    void reconcile_providerAnswersWithoutStatusKey_staysPending() {
        Transaction tx = agedTransaction("NO-STATUS", TransactionStatus.PENDING, MAX_AGE.plusHours(1));
        when(acquiringClient.getOrderStatus(anyString(), anyString(), any()))
                .thenReturn(Map.of("id", "ORD-NO-STATUS"));

        Assertions.assertEquals(1, reconciliationService.reconcilePendingTransactions());

        Transaction reloaded = reload(tx);
        Assertions.assertEquals(TransactionStatus.PENDING, reloaded.getStatus());
        Assertions.assertEquals("UNKNOWN", reloaded.getProviderResponse().get("mpStatusOutcome"));
        Assertions.assertNull(reloaded.getProviderResponse().get("reconciliationOutcome"));
    }

    // Числовой status раньше жёстко кастовался к String — ClassCastException посреди прогона.
    @Test
    void reconcile_providerAnswersWithNumericStatus_staysPendingWithoutClassCast() {
        Transaction tx = agedTransaction("NUMERIC", TransactionStatus.PENDING, MAX_AGE.plusHours(1));
        when(acquiringClient.getOrderStatus(anyString(), anyString(), any()))
                .thenReturn(Map.of("status", 200));

        Assertions.assertEquals(1, reconciliationService.reconcilePendingTransactions());

        Transaction reloaded = reload(tx);
        Assertions.assertEquals(TransactionStatus.PENDING, reloaded.getStatus());
        Assertions.assertEquals("UNKNOWN", reloaded.getProviderResponse().get("mpStatusOutcome"));
        Assertions.assertEquals("200", reloaded.getProviderResponse().get("mpProviderStatus"));
    }

    // 200 с пустым телом приходит из клиента как null: классифицировать нечего, отсюда UNKNOWN и
    // PENDING. Но накопленный payload (создание заказа, hppUrl, прошлый опрос) обязан уцелеть —
    // именно эта строка уходит на ручной разбор, и пустая карта не оставит разбирающему ничего.
    @Test
    void reconcile_providerAnswersWithNullPayload_keepsPreviousPayloadAndStaysPending() {
        Transaction tx = agedTransaction("NULL-BODY", TransactionStatus.PENDING, MAX_AGE.plusHours(1));
        tx.setProviderResponse(Map.of("hppUrl", "https://hpp.example/pay/ORD-NULL-BODY", "status", "Preparing"));
        transactionRepository.save(tx);
        when(acquiringClient.getOrderStatus(anyString(), anyString(), any()))
                .thenReturn(null);

        Assertions.assertEquals(1, reconciliationService.reconcilePendingTransactions());

        Transaction reloaded = reload(tx);
        Assertions.assertEquals(TransactionStatus.PENDING, reloaded.getStatus());
        Map<String, Object> providerResponse = reloaded.getProviderResponse();
        Assertions.assertNotNull(providerResponse);
        Assertions.assertEquals("https://hpp.example/pay/ORD-NULL-BODY", providerResponse.get("hppUrl"),
                "an empty answer must not wipe what was already known about the order");
        Assertions.assertEquals("Preparing", providerResponse.get("status"));
        Assertions.assertEquals("UNKNOWN", providerResponse.get("mpStatusOutcome"));
        Assertions.assertNull(providerResponse.get("reconciliationOutcome"));
    }

    // За give-up-age прогон перестаёт выбирать строку вовсе: иначе строки, которые нельзя закрыть
    // по таймауту (unknown или закрытые снаружи), как самые старые заняли бы все батчи и заморили
    // свежие платежи.
    @Test
    void reconcile_olderThanGiveUpAge_isNotSelectedAndAcquirerIsNotPolled() {
        Transaction givenUp = agedTransaction("GIVEN-UP", TransactionStatus.PENDING, GIVE_UP_AGE.plusHours(1));
        providerAnswers("FullyPaid");

        Assertions.assertEquals(0, reconciliationService.reconcilePendingTransactions());

        verify(acquiringClient, never()).getOrderStatus(anyString(), anyString(), any());
        Assertions.assertEquals(TransactionStatus.PENDING, statusOf(givenUp));
        Assertions.assertNull(reload(givenUp).getProviderResponse());
    }

    // Граница только верхняя: строка чуть внутри неё всё ещё попадает в прогон.
    @Test
    void reconcile_givenUpRowDoesNotStarveTheBatch() {
        for (int i = 0; i < BATCH_SIZE; i++) {
            agedTransaction("OLD-" + i, TransactionStatus.PENDING, GIVE_UP_AGE.plusHours(1 + i));
        }
        Transaction live = agedTransaction("LIVE", TransactionStatus.PENDING, Duration.ofMinutes(10));
        providerAnswers("FullyPaid");

        Assertions.assertEquals(1, reconciliationService.reconcilePendingTransactions());

        Assertions.assertEquals(TransactionStatus.SUCCESS, statusOf(live));
    }

    // RECON-STARVATION (Р-110): пакет брал самые старые PENDING, и строки, которые сверка закрыть не может
    // (незнакомый статус, SETTLED_OTHER, эквайер недоступен), занимали его каждый проход до give-up-age —
    // остальные не опрашивались неделю. Теперь опрошенная уходит в конец очереди, второй проход берёт новые.
    @Test
    void reconcile_rowsItCannotSettle_doNotStarveTheRestOfTheQueue() {
        int queue = BATCH_SIZE + 2;
        for (int i = 0; i < queue; i++) {
            agedTransaction("STUCK-" + i, TransactionStatus.PENDING, Duration.ofMinutes(10 + i));
        }
        providerAnswers("SomethingNew");

        Assertions.assertEquals(BATCH_SIZE, reconciliationService.reconcilePendingTransactions());
        Assertions.assertEquals(BATCH_SIZE, reconciliationService.reconcilePendingTransactions());

        ArgumentCaptor<String> polled = ArgumentCaptor.forClass(String.class);
        verify(acquiringClient, times(2 * BATCH_SIZE)).getOrderStatus(polled.capture(), anyString(), any());
        List<String> firstPass = polled.getAllValues().subList(0, BATCH_SIZE);
        List<String> secondPass = polled.getAllValues().subList(BATCH_SIZE, 2 * BATCH_SIZE);
        Assertions.assertEquals(List.of("ORD-STUCK-4", "ORD-STUCK-3", "ORD-STUCK-2"), firstPass);
        Assertions.assertEquals(List.of("ORD-STUCK-1", "ORD-STUCK-0", "ORD-STUCK-4"), secondPass,
                "the never-polled rows go first, then the longest-unpolled one");
        Assertions.assertEquals(queue, transactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == TransactionStatus.PENDING)
                .count());
    }

    // Фикстуры

    private void providerAnswers(String providerStatus) {
        when(acquiringClient.getOrderStatus(anyString(), anyString(), any()))
                .thenReturn(Map.of("status", providerStatus));
    }

    private Transaction agedTransaction(String key, TransactionStatus status, Duration age) {
        Transaction tx = createTransaction(key, status);
        backdate(tx, age);
        return tx;
    }

    private Transaction createTransaction(String key, TransactionStatus status) {
        PaymentLink link = paymentLinkRepository.save(PaymentLink.builder()
                .providerReference("RID-" + key)
                .merchantOrderId(key)
                .terminalId(TERMINAL_ID)
                .amount(new BigDecimal("100.00"))
                .currency("AZN")
                .description("Fixture for " + key)
                .paymentType(PaymentType.SMS)
                .usageType(UsageType.SINGLE)
                .currentPaymentsCount(0)
                .status(PaymentLinkStatus.ACTIVE)
                .build());

        return transactionRepository.save(Transaction.builder()
                .link(link)
                .ridByMerchant(UUID.randomUUID())
                .providerOrderId("ORD-" + key)
                .providerPassword("provider-password")
                .amount(new BigDecimal("100.00"))
                .refundedAmount(BigDecimal.ZERO)
                .status(status)
                .build());
    }

    // createdAt помечен CreationTimestamp и updatable = false, JPA его не выставит. Сдвиг уже
    // сохранённого значения вместо абсолютной метки не зависит от того, как драйвер кодирует время,
    // — и это правило стоит держать: в TerminalBlockingIntegrationTest абсолютная метка,
    // записанная мимо Hibernate, легла в локальной зоне вместо UTC и сломала проверку.
    //
    // Само вычитание — на диалекте PostgreSQL. Здесь стоял `TIMESTAMPADD`, которого у неё нет
    // вовсе: функция H2, и на настоящей СУБД запрос не разбирается.
    private void backdate(Transaction tx, Duration age) {
        int rows = jdbcTemplate.update(
                "UPDATE transactions SET created_at = created_at - CAST(? AS double precision) "
                        + "* INTERVAL '1 second' WHERE id = ?",
                (double) age.toSeconds(), tx.getId());
        Assertions.assertEquals(1, rows, "backdating helper must touch exactly one row");
    }

    private Transaction reload(Transaction tx) {
        return transactionRepository.findById(tx.getId()).orElseThrow();
    }

    private TransactionStatus statusOf(Transaction tx) {
        return reload(tx).getStatus();
    }

    // tx приходит прямо из save(), поэтому его link — настоящая сущность, а не прокси.
    private PaymentLinkStatus linkStatusOf(Transaction tx) {
        return paymentLinkRepository.findById(tx.getLink().getId()).orElseThrow().getStatus();
    }
}
