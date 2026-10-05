package az.millikart.ecom;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.ResourceNotFoundException;
import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.domain.ProviderLogin;
import az.millikart.ecom.domain.ProviderTerminal;
import az.millikart.ecom.dto.CursorPage;
import az.millikart.ecom.dto.EcomDashboardResponse;
import az.millikart.ecom.dto.EcomStatsResponse;
import az.millikart.ecom.dto.EcomTerminalResponse;
import az.millikart.ecom.dto.EcomTransactionFilter;
import az.millikart.ecom.dto.EcomTransactionResponse;
import az.millikart.ecom.repository.PortalPaymentsRepository;
import az.millikart.ecom.repository.PortalPaymentsRepository.PortalTerminal;
import az.millikart.ecom.repository.ProviderLoginRepository;
import az.millikart.ecom.repository.ProviderTerminalRepository;
import az.millikart.ecom.repository.TxpgStatementRow;
import az.millikart.ecom.repository.TxpgTransactionRepository;
import az.millikart.ecom.service.EcomScope;
import az.millikart.ecom.service.EcomPaymentType;
import az.millikart.ecom.service.EcomScopeService;
import az.millikart.ecom.service.EcomTransactionService;
import az.millikart.ecom.service.ProviderOrderAttemptService;
import az.millikart.ecom.service.TxpgRows;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

// Скоуп и предохранители выписки. SQL здесь не проверяется — для него TxpgTransactionRepositoryTest.
// Проверяется то, что решается на нашей стороне и ценой ошибки имеет чужие обороты на экране:
// мерчанты скоупа уходят в каждый запрос, фильтр по терминалу его не расширяет, период обязателен и ограничен.
class EcomTransactionScopeTest {

    private TxpgTransactionRepository repository;
    private EcomScopeService scope;
    private ProviderTerminalRepository providerTerminals;
    private ProviderLoginRepository providerLogins;
    private PortalPaymentsRepository portal;
    private ProviderOrderAttemptService attempts;
    private EcomTransactionService service;

    private static final EcomScope SCOPE = new EcomScope(List.of("E1120020"));
    private static final EcomScope NOTHING = new EcomScope(List.of());

    private final Instant from = Instant.parse("2026-09-01T00:00:00Z");
    private final Instant to = Instant.parse("2026-09-02T00:00:00Z");
    private final UserPrincipal principal =
            new UserPrincipal("1", "head@comp1.com", "COMPANY_HEAD", "comp-01");

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(TxpgTransactionRepository.class);
        scope = Mockito.mock(EcomScopeService.class);
        providerTerminals = Mockito.mock(ProviderTerminalRepository.class);
        providerLogins = Mockito.mock(ProviderLoginRepository.class);
        TxpgProperties properties = new TxpgProperties();
        properties.setMaxWindow(Duration.ofDays(92));
        properties.setMaxPageSize(200);
        portal = Mockito.mock(PortalPaymentsRepository.class);
        attempts = Mockito.mock(ProviderOrderAttemptService.class);
        service = new EcomTransactionService(repository, scope, providerTerminals, providerLogins, properties, portal,
                attempts);
    }

    // Компании без мерчантов — пустая выписка, и в базу шлюза за ней даже не ходим. Обратная
    // ветка — «нет мерчантов, значит фильтра нет» — это ровно то, как выглядит показ чужих оборотов.
    @Test
    void withoutLinkedTerminals_theStatementIsEmptyAndTheGatewayIsNotQueried() {
        when(scope.scopeFor(principal)).thenReturn(NOTHING);

        CursorPage<EcomTransactionResponse> page =
                service.list(from, to, null, null, null, null, null, null, null, null, principal);

        Assertions.assertTrue(page.content().isEmpty());
        Assertions.assertNull(page.nextCursor());
        verifyNoInteractions(repository);
    }

    @Test
    void withoutLinkedTerminals_anOrderIsNotFoundRatherThanEmpty() {
        when(scope.scopeFor(principal)).thenReturn(NOTHING);

        Assertions.assertThrows(ResourceNotFoundException.class, () -> service.order("175515", principal));
        verifyNoInteractions(repository);
    }

    @Test
    void withoutLinkedTerminals_statsAreZeroAndTheGatewayIsNotQueried() {
        when(scope.scopeFor(principal)).thenReturn(NOTHING);

        EcomStatsResponse stats = service.stats(from, to, null, null, principal);

        Assertions.assertEquals(0, stats.orderCount());
        verifyNoInteractions(repository);
    }

    // Мерчанты скоупа уходят и в выбор страницы, и в чтение её операций (Р-97); без фильтра — весь скоуп.
    @Test
    void theMerchantsOfTheScopeAreHandedToEveryQuery() {
        when(scope.scopeFor(principal)).thenReturn(new EcomScope(List.of("E1120020", "1234567")));
        when(repository.findOrderIds(any(), any(), anyInt())).thenReturn(List.of(175533L));

        service.list(from, to, null, null, null, null, null, null, null, null, principal);

        ArgumentCaptor<EcomTransactionFilter> filter = ArgumentCaptor.forClass(EcomTransactionFilter.class);
        verify(repository).findOrderIds(filter.capture(), isNull(), eq(26));
        Assertions.assertEquals(List.of("E1120020", "1234567"), filter.getValue().merchantRids());
        verify(repository).findRows(List.of(175533L), List.of("E1120020", "1234567"), from);
    }

    // Фильтр по терминалу сужает скоуп и никогда его не расширяет: чужой мерчант из запроса выпадает.
    @Test
    void aForeignMerchantInTheFilterIsDropped() {
        when(scope.scopeFor(principal)).thenReturn(new EcomScope(List.of("E1120020", "1234567")));

        service.list(from, to, List.of("1234567", "FOREIGN"), null, null, null, null, null, null, null, principal);
        service.stats(from, to, List.of("FOREIGN", "1234567"), null, principal);

        ArgumentCaptor<EcomTransactionFilter> page = ArgumentCaptor.forClass(EcomTransactionFilter.class);
        verify(repository).findOrderIds(page.capture(), isNull(), anyInt());
        Assertions.assertEquals(List.of("1234567"), page.getValue().merchantRids());
        ArgumentCaptor<EcomTransactionFilter> totals = ArgumentCaptor.forClass(EcomTransactionFilter.class);
        verify(repository).streamPeriodRows(totals.capture(), any());
        Assertions.assertEquals(List.of("1234567"), totals.getValue().merchantRids());
    }

    @Test
    void aFilterOfOnlyForeignMerchants_givesAnEmptyStatementWithoutAQuery() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);

        CursorPage<EcomTransactionResponse> page =
                service.list(from, to, List.of("FOREIGN"), null, null, null, null, null, null, null, principal);

        Assertions.assertTrue(page.content().isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void aPeriodIsRequired() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);

        Assertions.assertThrows(BusinessException.class,
                () -> service.list(null, to, null, null, null, null, null, null, null, null, principal));
        Assertions.assertThrows(BusinessException.class,
                () -> service.stats(from, null, null, null, principal));
    }

    @Test
    void anInvertedPeriodIsRefused() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);

        Assertions.assertThrows(BusinessException.class,
                () -> service.list(to, from, null, null, null, null, null, null, null, null, principal));
    }

    // Выписка за три года по операционной базе шлюза — полный скан на инстансе, который в этот
    // момент проводит авторизации.
    @Test
    void aPeriodLongerThanTheCeilingIsRefused() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        Instant farAway = from.plus(400, ChronoUnit.DAYS);

        Assertions.assertThrows(BusinessException.class,
                () -> service.list(from, farAway, null, null, null, null, null, null, null, null, principal));
        verifyNoInteractions(repository);
    }

    @Test
    void thePageSizeIsClampedToTheCeiling() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);

        service.list(from, to, null, null, null, null, null, null, null, 100_000, principal);

        // Запрошенная страница плюс один номер на вопрос «есть ли что-то дальше».
        verify(repository).findOrderIds(any(), isNull(), eq(201));
    }

    // Лишний номер не загружается и не отдаётся, а становится курсором следующей страницы.
    @Test
    void theExtraOrderIsNotLoaded_andBecomesTheNextCursor() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        when(repository.findOrderIds(any(), any(), anyInt())).thenReturn(List.of(175662L, 175661L, 175605L));
        when(repository.findRows(any(), any(), any())).thenReturn(rowsOf("175662", "175661"));

        CursorPage<EcomTransactionResponse> page =
                service.list(from, to, null, null, null, null, null, null, null, 2, principal);

        verify(repository).findRows(eq(List.of(175662L, 175661L)), any(), any());
        Assertions.assertEquals(2, page.content().size());
        Assertions.assertNotNull(page.nextCursor());
    }

    @Test
    void theLastPageCarriesNoCursor() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        when(repository.findOrderIds(any(), any(), anyInt())).thenReturn(List.of(175662L, 175661L));
        when(repository.findRows(any(), any(), any())).thenReturn(rowsOf("175662", "175661"));

        CursorPage<EcomTransactionResponse> page =
                service.list(from, to, null, null, null, null, null, null, null, 2, principal);

        Assertions.assertEquals(2, page.content().size());
        Assertions.assertNull(page.nextCursor());
    }

    // Курсор прошлой страницы читается обратно в номер её последнего заказа.
    @Test
    void theCursorRoundTripsBackIntoAPosition() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        when(repository.findOrderIds(any(), any(), anyInt())).thenReturn(List.of(175662L, 175661L, 175605L));

        String cursor = service.list(from, to, null, null, null, null, null, null, null, 2, principal).nextCursor();
        service.list(from, to, null, null, null, null, null, null, cursor, 2, principal);

        ArgumentCaptor<Long> before = ArgumentCaptor.forClass(Long.class);
        verify(repository, times(2)).findOrderIds(any(), before.capture(), anyInt());
        Assertions.assertNull(before.getAllValues().get(0));
        Assertions.assertEquals(175661L, before.getAllValues().get(1));
    }

    // Битый курсор — это 400 клиенту, а не 500 у нас.
    @Test
    void anUnreadableCursorIsARequestError() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);

        for (String cursor : List.of("not-a-cursor", "!!!")) {
            Assertions.assertThrows(BusinessException.class,
                    () -> service.list(from, to, null, null, null, null, null, null, cursor, 2, principal), cursor);
        }
        verify(repository, never()).findOrderIds(any(), any(), anyInt());
    }

    @Test
    void theOrderCardIsReadWithinTheScope_andWithoutAPeriod() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        when(repository.findRows(any(), any(), any())).thenReturn(rowsOf("175533"));

        EcomTransactionResponse order = service.order("175533", principal);

        Assertions.assertEquals("175533", order.orderId());
        verify(repository).findRows(List.of(175533L), List.of("E1120020"), null);
    }

    // Номер заказа у провайдера — число: всё прочее в адресе не существует, и в шлюз за ним не ходим.
    @Test
    void aNonNumericOrderIdIsNotFound_andTheGatewayIsNotQueried() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);

        for (String orderId : List.of("abc", "-1", "0", "1 or 1=1", "12345678901234567890")) {
            Assertions.assertThrows(ResourceNotFoundException.class, () -> service.order(orderId, principal), orderId);
        }
        verifyNoInteractions(repository);
    }

    // Чужой, несуществующий и незавершённый заказ для запроса неотличимы — всё это пустой ответ шлюза.
    @Test
    void anOrderTheGatewayDoesNotReturnIsNotFound() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        when(repository.findRows(any(), any(), any())).thenReturn(List.of());

        Assertions.assertThrows(ResourceNotFoundException.class, () -> service.order("175700", principal));
    }

    @Test
    void statsFoldTheWholeStreamOfThePeriod() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        doAnswer(invocation -> {
            Consumer<TxpgStatementRow> sink = invocation.getArgument(1);
            TxpgRows.testStandExport().forEach(sink);
            return null;
        }).when(repository).streamPeriodRows(any(), any());

        EcomStatsResponse stats = service.stats(from, to, null, null, principal);

        Assertions.assertEquals(16, stats.orderCount());
        Assertions.assertEquals(4L, stats.statusCounts().get("CANCELED"));
    }

    // Фильтр строится из нашей базы, без шлюза. Мерчант без терминала в слепке остаётся в списке с
    // названием из слепка логинов: иначе по нему нельзя было бы отфильтровать собственные платежи.
    @Test
    void terminalsForTheFilterComeFromTheScope() {
        when(scope.scopeFor(principal)).thenReturn(new EcomScope(List.of("M-1", "M-2", "M-3")));
        when(providerTerminals.findAllById(List.of("M-1", "M-2", "M-3"))).thenReturn(List.of(
                ProviderTerminal.builder().rid("M-2").title("Bazar").login("BS00002").terminalRid("TID-2").build()));
        when(providerLogins.findByMerchantRidIn(List.of("M-1", "M-3"))).thenReturn(List.of(
                ProviderLogin.builder().login("bazarstore@company.com").merchantRid("M-1").merchantTitle("Bazar Xirdalan").build()));

        List<EcomTerminalResponse> terminals = service.terminals(principal);

        Assertions.assertEquals(List.of(
                new EcomTerminalResponse("M-2", "Bazar", "BS00002", "TID-2"),
                new EcomTerminalResponse("M-1", "Bazar Xirdalan", null, null),
                new EcomTerminalResponse("M-3", null, null, null)), terminals);
        verifyNoInteractions(repository);
    }

    // Р-87: тип оплаты уходит в фильтр и страницы, и итогов — условие строит SQL.
    @Test
    void thePaymentTypeGoesIntoTheFilterOfThePageAndOfTheTotals() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);

        service.list(from, to, null, null, null, null, null, "DMS", null, null, principal);
        service.stats(from, to, null, "SMS", principal);

        ArgumentCaptor<EcomTransactionFilter> page = ArgumentCaptor.forClass(EcomTransactionFilter.class);
        verify(repository).findOrderIds(page.capture(), isNull(), anyInt());
        Assertions.assertEquals(EcomPaymentType.DMS, page.getValue().paymentType());
        ArgumentCaptor<EcomTransactionFilter> totals = ArgumentCaptor.forClass(EcomTransactionFilter.class);
        verify(repository).streamPeriodRows(totals.capture(), any());
        Assertions.assertEquals(EcomPaymentType.SMS, totals.getValue().paymentType());
    }

    // Незнакомое значение — 400 и никакого похода в шлюз. Сравнение строгое: «success» — не SUCCESS.
    @Test
    void anUnknownStatusOrPaymentTypeIsARequestError() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);

        for (String status : List.of("PAID", "success", "FullyPaid")) {
            Assertions.assertThrows(BusinessException.class,
                    () -> service.list(from, to, null, null, null, null, status, null, null, null, principal), status);
        }
        for (String type : List.of("sms", "Order_DMS", "POS")) {
            Assertions.assertThrows(BusinessException.class,
                    () -> service.list(from, to, null, null, null, null, null, type, null, null, principal), type);
            Assertions.assertThrows(BusinessException.class,
                    () -> service.stats(from, to, null, type, principal), type);
        }
        verifyNoInteractions(repository);
    }

    // Р-87: статус считается в Java, поэтому заказы читаются пачками и отбираются после сборки. Пачка —
    // max-page-size; страница набирается из нескольких пачек, курсор — последний просмотренный заказ.
    @Test
    void aStatusFilterSkipsOtherStatuses_andFillsThePageFromTheNextBatch() {
        EcomTransactionService narrow = serviceWith(2, 1000);
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        gatewayWith(Map.of(
                105L, TxpgRows.order("105", "FullyPaid", "Preparing", "10", TxpgRows.single("10")),
                104L, TxpgRows.order("104", "Closed", "Authorized", "10", TxpgRows.auth("10")),
                103L, TxpgRows.order("103", "Closed", "Authorized", "10", TxpgRows.auth("10")),
                102L, TxpgRows.order("102", "FullyPaid", "Preparing", "10", TxpgRows.single("10")),
                101L, TxpgRows.order("101", "FullyPaid", "Preparing", "10", TxpgRows.single("10"))));

        CursorPage<EcomTransactionResponse> first =
                narrow.list(from, to, null, null, null, null, "SUCCESS", null, null, 2, principal);

        Assertions.assertEquals(List.of("105", "102"), first.content().stream().map(EcomTransactionResponse::orderId).toList());
        Assertions.assertNotNull(first.nextCursor());

        CursorPage<EcomTransactionResponse> second =
                narrow.list(from, to, null, null, null, null, "SUCCESS", null, first.nextCursor(), 2, principal);

        Assertions.assertEquals(List.of("101"), second.content().stream().map(EcomTransactionResponse::orderId).toList());
        Assertions.assertNull(second.nextCursor());
    }

    // Потолок просмотра: редкий статус не сканирует весь период за одно нажатие. Страница приходит
    // короче — здесь пустой — но с курсором, и следующий запрос продолжает с последнего просмотренного.
    @Test
    void aStatusFilterStopsAtTheScanLimit_andHandsBackACursorToContinue() {
        EcomTransactionService narrow = serviceWith(2, 2);
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        gatewayWith(Map.of(
                105L, TxpgRows.order("105", "FullyPaid", "Preparing", "10", TxpgRows.single("10")),
                104L, TxpgRows.order("104", "FullyPaid", "Preparing", "10", TxpgRows.single("10")),
                103L, TxpgRows.order("103", "Closed", "Authorized", "10", TxpgRows.auth("10"))));

        CursorPage<EcomTransactionResponse> page =
                narrow.list(from, to, null, null, null, null, "CANCELED", null, null, 2, principal);

        Assertions.assertTrue(page.content().isEmpty());
        Assertions.assertNotNull(page.nextCursor());
        verify(repository, times(1)).findOrderIds(any(), any(), anyInt());

        CursorPage<EcomTransactionResponse> next =
                narrow.list(from, to, null, null, null, null, "CANCELED", null, page.nextCursor(), 2, principal);

        Assertions.assertEquals(List.of("103"), next.content().stream().map(EcomTransactionResponse::orderId).toList());
        Assertions.assertNull(next.nextCursor());
    }

    // ─── Р-91: сводка главной ────────────────────────────────────────────────

    // Пользователю без терминалов — пустая сводка, и в базу шлюза за ней не ходим, как и за выпиской.
    @Test
    void dashboardWithoutLinkedTerminals_isEmptyAndTheGatewayIsNotQueried() {
        when(scope.scopeFor(principal)).thenReturn(NOTHING);

        EcomDashboardResponse summary = service.dashboard(from, to, principal);

        Assertions.assertTrue(summary.totals().isEmpty());
        Assertions.assertTrue(summary.statusCounts().values().stream().allMatch(count -> count == 0L));
        verifyNoInteractions(repository);
    }

    // Сводка — по всему скоупу, без сужения по терминалу и типу; суммы и статусы — тем же проходом по строкам
    // периода, что итоги выписки, поэтому за тот же период они совпадают. Подпись терминала — из слепка.
    @Test
    void dashboardReadsTheWholeScope_andMatchesTheStatementTotals() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        doAnswer(invocation -> {
            Consumer<TxpgStatementRow> sink = invocation.getArgument(1);
            TxpgRows.testStandExport().forEach(sink);
            return null;
        }).when(repository).streamPeriodRows(any(), any());
        when(providerTerminals.findAllById(any())).thenReturn(List.of(
                ProviderTerminal.builder().rid(TxpgRows.MERCHANT_RID).title("Bazar").login("BS00001").terminalRid("TID-1").build()));

        EcomDashboardResponse summary = service.dashboard(from, to, principal);
        EcomStatsResponse stats = service.stats(from, to, null, null, principal);

        ArgumentCaptor<EcomTransactionFilter> filter = ArgumentCaptor.forClass(EcomTransactionFilter.class);
        verify(repository, times(2)).streamPeriodRows(filter.capture(), any());
        Assertions.assertEquals(SCOPE.merchantRids(), filter.getAllValues().getFirst().merchantRids());
        Assertions.assertNull(filter.getAllValues().getFirst().paymentType());

        Assertions.assertEquals(stats.statusCounts(), summary.statusCounts());
        Assertions.assertEquals(stats.orderCount(),
                summary.totals().stream().mapToLong(EcomDashboardResponse.CurrencyTotals::orderCount).sum());
        for (EcomStatsResponse.CurrencyTotal total : stats.totals()) {
            EcomDashboardResponse.CurrencyTotals same = summary.totals().stream()
                    .filter(t -> java.util.Objects.equals(t.currency(), total.currency())).findFirst().orElseThrow();
            Assertions.assertEquals(0, total.capturedAmount().compareTo(same.capturedAmount()));
            Assertions.assertEquals(0, total.refundedAmount().compareTo(same.refundedAmount()));
        }
        Assertions.assertEquals("BS00001", summary.topTerminals().getFirst().login());
        Assertions.assertEquals("TID-1", summary.topTerminals().getFirst().terminalRid());
        Assertions.assertEquals("Bazar", summary.topTerminals().getFirst().title());
        Assertions.assertEquals("Asia/Baku", summary.window().zone());
    }

    @Test
    void dashboardPeriodIsRequiredAndBounded() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);

        Assertions.assertThrows(BusinessException.class, () -> service.dashboard(null, to, principal));
        Assertions.assertThrows(BusinessException.class,
                () -> service.dashboard(from, from.plus(400, ChronoUnit.DAYS), principal));
        verifyNoInteractions(repository);
    }

    private EcomTransactionService serviceWith(int maxPageSize, int statusScanLimit) {
        TxpgProperties properties = new TxpgProperties();
        properties.setMaxWindow(Duration.ofDays(92));
        properties.setMaxPageSize(maxPageSize);
        properties.setStatusScanLimit(statusScanLimit);
        return new EcomTransactionService(repository, scope, providerTerminals, providerLogins, properties, portal,
                attempts);
    }

    // Шлюз из заказов: номера — от новых к старым ниже курсора и не больше запрошенного, строки — по номерам.
    private void gatewayWith(Map<Long, List<TxpgStatementRow>> orders) {
        List<Long> ids = orders.keySet().stream().sorted((a, b) -> Long.compare(b, a)).toList();
        when(repository.findOrderIds(any(), any(), anyInt())).thenAnswer(invocation -> {
            Long before = invocation.getArgument(1);
            int limit = invocation.getArgument(2);
            return ids.stream().filter(id -> before == null || id < before).limit(limit).toList();
        });
        when(repository.findRows(any(), any(), any())).thenAnswer(invocation -> {
            List<Long> wanted = invocation.getArgument(0);
            List<TxpgStatementRow> rows = new ArrayList<>();
            wanted.forEach(id -> rows.addAll(orders.get(id)));
            return rows;
        });
    }

    // --- кнопки возврата и списания (Р-124) -----------------------------------------------------------

    // Мерчант заказа заведён терминалом портала, у компании терминала есть креды — возврат активен на остаток,
    // списано минус возвращено; у покупки SMS списания нет вовсе.
    @Test
    void theOrderCard_offersARefundOfWhatIsLeft_whenItsMerchantIsAPortalTerminal() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        when(repository.findRows(any(), any(), any()))
                .thenReturn(TxpgRows.order("175900", "FullyPaid", null, "40", TxpgRows.single("40")));
        when(portal.terminalOfMerchant(TxpgRows.MERCHANT_RID)).thenReturn(Optional.of(new PortalTerminal(7, "comp-01")));
        when(portal.hasProviderCredentials("comp-01")).thenReturn(true);

        EcomTransactionResponse order = service.order("175900", principal);

        Assertions.assertTrue(order.actions().refund().enabled());
        Assertions.assertEquals(0, new BigDecimal("40").compareTo(order.actions().refund().maxAmount()));
        Assertions.assertNull(order.actions().capture(), "an SMS purchase has no capture");
        Assertions.assertNull(order.portalTransactionId());
    }

    // Требование заказчика: мерчанта нет среди терминалов портала — кнопка видна, но выключена и говорит почему.
    @Test
    void anOrderOfAMerchantThatIsNoPortalTerminal_saysSo() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        when(repository.findRows(any(), any(), any()))
                .thenReturn(TxpgRows.order("175901", "FullyPaid", null, "40", TxpgRows.single("40")));

        EcomTransactionResponse order = service.order("175901", principal);

        Assertions.assertFalse(order.actions().refund().enabled());
        Assertions.assertEquals("TERMINAL_NOT_IN_PORTAL", order.actions().refund().reason());
    }

    // Заказ завёл портал — проводит его pbl: вместо кнопок номер операции портала, и деньги учитываются в одном месте.
    @Test
    void anOrderThePortalCreated_pointsToItsPortalTransactionInsteadOfButtons() {
        UUID portalTransaction = UUID.randomUUID();
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        when(repository.findRows(any(), any(), any()))
                .thenReturn(TxpgRows.order("175902", "FullyPaid", null, "40", TxpgRows.single("40")));
        when(portal.portalTransactionOf("175902")).thenReturn(Optional.of(portalTransaction));

        EcomTransactionResponse order = service.order("175902", principal);

        Assertions.assertNull(order.actions());
        Assertions.assertEquals(portalTransaction.toString(), order.portalTransactionId());
        verify(portal, never()).terminalOfMerchant(any());
    }

    // Строки выписки кнопок не несут: на каждую строку — запросы к терминалам, кредам и операциям портала.
    @Test
    void statementRowsCarryNoButtons() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        when(repository.findOrderIds(any(), any(), anyInt())).thenReturn(List.of(175662L));
        when(repository.findRows(any(), any(), any())).thenReturn(rowsOf("175662"));

        CursorPage<EcomTransactionResponse> page =
                service.list(from, to, null, null, null, null, null, null, null, null, principal);

        Assertions.assertNull(page.content().get(0).actions());
        verifyNoInteractions(portal, attempts);
    }

    private static List<TxpgStatementRow> rowsOf(String... orderIds) {
        return TxpgRows.testStandExport().stream()
                .filter(row -> List.of(orderIds).contains(row.orderId()))
                .toList();
    }
}
