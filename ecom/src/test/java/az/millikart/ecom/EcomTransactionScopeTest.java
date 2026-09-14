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
import az.millikart.ecom.domain.ProviderTerminal;
import az.millikart.ecom.dto.CursorPage;
import az.millikart.ecom.dto.EcomStatsResponse;
import az.millikart.ecom.dto.EcomTerminalResponse;
import az.millikart.ecom.dto.EcomTransactionFilter;
import az.millikart.ecom.dto.EcomTransactionResponse;
import az.millikart.ecom.repository.ProviderTerminalRepository;
import az.millikart.ecom.repository.TxpgStatementRow;
import az.millikart.ecom.repository.TxpgTransactionRepository;
import az.millikart.ecom.service.EcomScope;
import az.millikart.ecom.service.EcomScopeService;
import az.millikart.ecom.service.EcomTransactionService;
import az.millikart.ecom.service.TxpgRows;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

// Скоуп и предохранители выписки. SQL здесь не проверяется — для него TxpgTransactionRepositoryTest.
// Проверяется то, что решается на нашей стороне и ценой ошибки имеет чужие обороты на экране:
// логины скоупа уходят в каждый запрос, фильтр по терминалу его не расширяет, период обязателен и ограничен.
class EcomTransactionScopeTest {

    private TxpgTransactionRepository repository;
    private EcomScopeService scope;
    private ProviderTerminalRepository providerTerminals;
    private EcomTransactionService service;

    private static final EcomScope SCOPE = new EcomScope(List.of("BS00001"), List.of("E1120020"));

    private final Instant from = Instant.parse("2026-09-01T00:00:00Z");
    private final Instant to = Instant.parse("2026-09-02T00:00:00Z");
    private final UserPrincipal principal =
            new UserPrincipal("1", "head@comp1.com", "COMPANY_HEAD", "comp-01");

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(TxpgTransactionRepository.class);
        scope = Mockito.mock(EcomScopeService.class);
        providerTerminals = Mockito.mock(ProviderTerminalRepository.class);
        TxpgProperties properties = new TxpgProperties();
        properties.setMaxWindow(Duration.ofDays(92));
        properties.setMaxPageSize(200);
        service = new EcomTransactionService(repository, scope, providerTerminals, properties);
    }

    // Мерчанту без терминалов — пустая выписка, и в базу шлюза за ней даже не ходим. Обратная
    // ветка — «нет терминалов, значит фильтра нет» — это ровно то, как выглядит показ чужих оборотов.
    @Test
    void withoutLinkedTerminals_theStatementIsEmptyAndTheGatewayIsNotQueried() {
        when(scope.scopeFor(principal)).thenReturn(new EcomScope(List.of(), List.of()));

        CursorPage<EcomTransactionResponse> page =
                service.list(from, to, null, null, null, null, null, null, principal);

        Assertions.assertTrue(page.content().isEmpty());
        Assertions.assertNull(page.nextCursor());
        verifyNoInteractions(repository);
    }

    @Test
    void withoutLinkedTerminals_anOrderIsNotFoundRatherThanEmpty() {
        when(scope.scopeFor(principal)).thenReturn(new EcomScope(List.of(), List.of()));

        Assertions.assertThrows(ResourceNotFoundException.class, () -> service.order("175515", principal));
        verifyNoInteractions(repository);
    }

    @Test
    void withoutLinkedTerminals_statsAreZeroAndTheGatewayIsNotQueried() {
        when(scope.scopeFor(principal)).thenReturn(new EcomScope(List.of(), List.of()));

        EcomStatsResponse stats = service.stats(from, to, null, principal);

        Assertions.assertEquals(0, stats.orderCount());
        verifyNoInteractions(repository);
    }

    // Логины скоупа уходят и в выбор страницы, и в чтение её операций; без фильтра сужения по мерчанту нет.
    @Test
    void theLoginsOfTheScopeAreHandedToEveryQuery() {
        when(scope.scopeFor(principal)).thenReturn(new EcomScope(
                List.of("BS00001", "BS00002"), List.of("E1120020", "1234567")));
        when(repository.findOrderIds(any(), any(), anyInt())).thenReturn(List.of(175533L));

        service.list(from, to, null, null, null, null, null, null, principal);

        ArgumentCaptor<EcomTransactionFilter> filter = ArgumentCaptor.forClass(EcomTransactionFilter.class);
        verify(repository).findOrderIds(filter.capture(), isNull(), eq(26));
        Assertions.assertEquals(List.of("BS00001", "BS00002"), filter.getValue().logins());
        Assertions.assertNull(filter.getValue().merchantRids());
        verify(repository).findRows(List.of(175533L), List.of("BS00001", "BS00002"), from);
    }

    // Терминал, заведённый без терминала провайдера, в выписке виден: скоуп идёт по логину.
    @Test
    void aTerminalWithoutAProviderLink_stillHasAStatement() {
        when(scope.scopeFor(principal)).thenReturn(new EcomScope(List.of("BS00001"), List.of()));

        service.list(from, to, null, null, null, null, null, null, principal);
        service.stats(from, to, null, principal);

        verify(repository).findOrderIds(any(), isNull(), anyInt());
        verify(repository).streamPeriodRows(any(), any());
    }

    // Фильтр по терминалу сужает скоуп и никогда его не расширяет: чужой мерчант из запроса выпадает.
    @Test
    void aForeignMerchantInTheFilterIsDropped() {
        when(scope.scopeFor(principal)).thenReturn(new EcomScope(
                List.of("BS00001", "BS00002"), List.of("E1120020", "1234567")));

        service.list(from, to, List.of("1234567", "FOREIGN"), null, null, null, null, null, principal);

        ArgumentCaptor<EcomTransactionFilter> filter = ArgumentCaptor.forClass(EcomTransactionFilter.class);
        verify(repository).findOrderIds(filter.capture(), isNull(), anyInt());
        Assertions.assertEquals(List.of("1234567"), filter.getValue().merchantRids());
        Assertions.assertEquals(List.of("BS00001", "BS00002"), filter.getValue().logins());
    }

    @Test
    void aFilterOfOnlyForeignMerchants_givesAnEmptyStatementWithoutAQuery() {
        when(scope.scopeFor(principal)).thenReturn(new EcomScope(List.of("BS00001"), List.of("E1120020")));

        CursorPage<EcomTransactionResponse> page =
                service.list(from, to, List.of("FOREIGN"), null, null, null, null, null, principal);

        Assertions.assertTrue(page.content().isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void aPeriodIsRequired() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);

        Assertions.assertThrows(BusinessException.class,
                () -> service.list(null, to, null, null, null, null, null, null, principal));
        Assertions.assertThrows(BusinessException.class,
                () -> service.stats(from, null, null, principal));
    }

    @Test
    void anInvertedPeriodIsRefused() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);

        Assertions.assertThrows(BusinessException.class,
                () -> service.list(to, from, null, null, null, null, null, null, principal));
    }

    // Выписка за три года по операционной базе шлюза — полный скан на инстансе, который в этот
    // момент проводит авторизации.
    @Test
    void aPeriodLongerThanTheCeilingIsRefused() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        Instant farAway = from.plus(400, ChronoUnit.DAYS);

        Assertions.assertThrows(BusinessException.class,
                () -> service.list(from, farAway, null, null, null, null, null, null, principal));
        verifyNoInteractions(repository);
    }

    @Test
    void thePageSizeIsClampedToTheCeiling() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);

        service.list(from, to, null, null, null, null, null, 100_000, principal);

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
                service.list(from, to, null, null, null, null, null, 2, principal);

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
                service.list(from, to, null, null, null, null, null, 2, principal);

        Assertions.assertEquals(2, page.content().size());
        Assertions.assertNull(page.nextCursor());
    }

    // Курсор прошлой страницы читается обратно в номер её последнего заказа.
    @Test
    void theCursorRoundTripsBackIntoAPosition() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        when(repository.findOrderIds(any(), any(), anyInt())).thenReturn(List.of(175662L, 175661L, 175605L));

        String cursor = service.list(from, to, null, null, null, null, null, 2, principal).nextCursor();
        service.list(from, to, null, null, null, null, cursor, 2, principal);

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
                    () -> service.list(from, to, null, null, null, null, cursor, 2, principal), cursor);
        }
        verify(repository, never()).findOrderIds(any(), any(), anyInt());
    }

    @Test
    void theOrderCardIsReadWithinTheScope_andWithoutAPeriod() {
        when(scope.scopeFor(principal)).thenReturn(SCOPE);
        when(repository.findRows(any(), any(), any())).thenReturn(rowsOf("175533"));

        EcomTransactionResponse order = service.order("175533", principal);

        Assertions.assertEquals("175533", order.orderId());
        verify(repository).findRows(List.of(175533L), List.of("BS00001"), null);
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

        EcomStatsResponse stats = service.stats(from, to, null, principal);

        Assertions.assertEquals(16, stats.orderCount());
        Assertions.assertEquals(4L, stats.statusCounts().get("CANCELED"));
    }

    // Фильтр строится из нашей базы, без шлюза. Терминал, которого нет в слепке, остаётся в списке
    // с пустым названием: иначе по нему нельзя было бы отфильтровать собственные платежи.
    @Test
    void terminalsForTheFilterComeFromTheScope() {
        when(scope.scopeFor(principal)).thenReturn(new EcomScope(List.of("BS00002", "shop_login"), List.of("M-2", "M-1")));
        when(providerTerminals.findAllById(List.of("M-2", "M-1"))).thenReturn(List.of(
                ProviderTerminal.builder().rid("M-2").title("Bazar").login("BS00002").build()));

        List<EcomTerminalResponse> terminals = service.terminals(principal);

        Assertions.assertEquals(List.of(
                new EcomTerminalResponse("M-2", "Bazar", "BS00002"),
                new EcomTerminalResponse("M-1", null, null)), terminals);
        verifyNoInteractions(repository);
    }

    private static List<TxpgStatementRow> rowsOf(String... orderIds) {
        return TxpgRows.testStandExport().stream()
                .filter(row -> List.of(orderIds).contains(row.orderId()))
                .toList();
    }
}
