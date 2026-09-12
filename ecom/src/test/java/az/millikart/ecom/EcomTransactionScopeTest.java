package az.millikart.ecom;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.ResourceNotFoundException;
import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.dto.CursorPage;
import az.millikart.ecom.dto.EcomTransactionFilter;
import az.millikart.ecom.dto.EcomTransactionResponse;
import az.millikart.ecom.repository.TxpgTransactionRepository;
import az.millikart.ecom.service.EcomTransactionService;
import az.millikart.ecom.service.EcomScopeService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Скоуп и предохранители выписки.
 *
 * Сам SQL здесь не проверяется — для этого нужен живой Oracle со схемой шлюза. Проверяется то,
 * что решается на нашей стороне и ценой ошибки имеет чужие обороты на экране мерчанта: список
 * мерчантов уходит в каждый запрос, пустой список означает пустую выписку, период обязателен
 * и ограничен, страница не растёт без предела.
 */
class EcomTransactionScopeTest {

    private TxpgTransactionRepository repository;
    private EcomScopeService scope;
    private EcomTransactionService service;

    private final Instant from = Instant.parse("2026-09-01T00:00:00Z");
    private final Instant to = Instant.parse("2026-09-02T00:00:00Z");
    private final UserPrincipal principal =
            new UserPrincipal("1", "head@comp1.com", "COMPANY_HEAD", "comp-01");

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(TxpgTransactionRepository.class);
        scope = Mockito.mock(EcomScopeService.class);
        TxpgProperties properties = new TxpgProperties();
        properties.setMaxWindow(Duration.ofDays(92));
        properties.setMaxPageSize(200);
        service = new EcomTransactionService(repository, scope, properties);
    }

    // Мерчанту без привязанных терминалов показывается пустая выписка, и в базу шлюза мы за ней даже не идём.
    // Обратная ветка — «нет терминалов, значит фильтра нет» — это ровно то, как выглядит показ
    // чужих оборотов.
    @Test
    void withoutLinkedTerminals_theStatementIsEmptyAndTheGatewayIsNotQueried() {
        when(scope.merchantRidsFor(principal)).thenReturn(List.of());

        CursorPage<EcomTransactionResponse> page =
                service.list(from, to, null, null, null, null, null, null, principal);

        Assertions.assertTrue(page.content().isEmpty());
        Assertions.assertNull(page.nextCursor());
        verifyNoInteractions(repository);
    }

    @Test
    void withoutLinkedTerminals_anOrderIsNotFoundRatherThanEmpty() {
        when(scope.merchantRidsFor(principal)).thenReturn(List.of());

        Assertions.assertThrows(ResourceNotFoundException.class,
                () -> service.operations("175515", principal));
        verify(repository, never()).findOperations(any(), any());
    }

    // Список мерчантов уходит в запрос всегда и ровно тот, что дали терминалы компании.
    @Test
    void theLinkedMerchantsAreHandedToEveryQuery() {
        when(scope.merchantRidsFor(principal)).thenReturn(List.of("E1120020", "1234567"));
        when(repository.findPage(any(), any())).thenReturn(List.of());

        service.list(from, to, null, null, null, null, null, null, principal);

        ArgumentCaptor<EcomTransactionFilter> filter = ArgumentCaptor.forClass(EcomTransactionFilter.class);
        verify(repository).findPage(filter.capture(), eq(null));
        Assertions.assertEquals(List.of("E1120020", "1234567"), filter.getValue().merchantRids());
    }

    @Test
    void aPeriodIsRequired() {
        when(scope.merchantRidsFor(principal)).thenReturn(List.of("E1120020"));

        Assertions.assertThrows(BusinessException.class,
                () -> service.list(null, to, null, null, null, null, null, null, principal));
        Assertions.assertThrows(BusinessException.class,
                () -> service.list(from, null, null, null, null, null, null, null, principal));
    }

    @Test
    void anInvertedPeriodIsRefused() {
        when(scope.merchantRidsFor(principal)).thenReturn(List.of("E1120020"));

        Assertions.assertThrows(BusinessException.class,
                () -> service.list(to, from, null, null, null, null, null, null, principal));
    }

    // Выписка за три года по операционной базе шлюза — это полный скан на инстансе, который
    // в этот момент проводит авторизации.
    @Test
    void aPeriodLongerThanTheCeilingIsRefused() {
        when(scope.merchantRidsFor(principal)).thenReturn(List.of("E1120020"));
        Instant farAway = from.plus(400, ChronoUnit.DAYS);

        Assertions.assertThrows(BusinessException.class,
                () -> service.list(from, farAway, null, null, null, null, null, null, principal));
    }

    @Test
    void thePageSizeIsClampedToTheCeiling() {
        when(scope.merchantRidsFor(principal)).thenReturn(List.of("E1120020"));
        when(repository.findPage(any(), any())).thenReturn(List.of());

        service.list(from, to, null, null, null, null, null, 100_000, principal);

        ArgumentCaptor<EcomTransactionFilter> filter = ArgumentCaptor.forClass(EcomTransactionFilter.class);
        verify(repository).findPage(filter.capture(), eq(null));
        // Запрошенная страница плюс одна лишняя строка на вопрос «есть ли что-то дальше».
        Assertions.assertEquals(201, filter.getValue().pageSize());
    }

    // Лишняя строка не отдаётся наружу, а превращается в курсор следующей страницы.
    @Test
    void theExtraRowBecomesTheNextCursorAndIsNotReturned() {
        when(scope.merchantRidsFor(principal)).thenReturn(List.of("E1120020"));
        when(repository.findPage(any(), any())).thenReturn(rows(3));

        CursorPage<EcomTransactionResponse> page =
                service.list(from, to, null, null, null, null, null, 2, principal);

        Assertions.assertEquals(2, page.content().size());
        Assertions.assertNotNull(page.nextCursor());
    }

    @Test
    void theLastPageCarriesNoCursor() {
        when(scope.merchantRidsFor(principal)).thenReturn(List.of("E1120020"));
        when(repository.findPage(any(), any())).thenReturn(rows(2));

        CursorPage<EcomTransactionResponse> page =
                service.list(from, to, null, null, null, null, null, 2, principal);

        Assertions.assertEquals(2, page.content().size());
        Assertions.assertNull(page.nextCursor());
    }

    // Курсор, выданный прошлой страницей, читается обратно в ту же позицию.
    @Test
    void theCursorRoundTripsBackIntoAPosition() {
        when(scope.merchantRidsFor(principal)).thenReturn(List.of("E1120020"));
        when(repository.findPage(any(), any())).thenReturn(rows(3));

        String cursor = service.list(from, to, null, null, null, null, null, 2, principal).nextCursor();
        when(repository.findPage(any(), any())).thenReturn(List.of());
        service.list(from, to, null, null, null, null, cursor, 2, principal);

        // Два вызова: первая страница без курсора и вторая — с ним. Интересен второй.
        ArgumentCaptor<TxpgTransactionRepository.Cursor> captured =
                ArgumentCaptor.forClass(TxpgTransactionRepository.Cursor.class);
        verify(repository, times(2)).findPage(any(), captured.capture());
        TxpgTransactionRepository.Cursor position = captured.getAllValues().get(1);
        Assertions.assertEquals("order-2", position.orderId());
        Assertions.assertEquals(from.plusSeconds(2), position.lastOperationAt());
    }

    // Битый курсор — это 400 клиенту, а не 500 у нас.
    @Test
    void anUnreadableCursorIsARequestError() {
        when(scope.merchantRidsFor(principal)).thenReturn(List.of("E1120020"));

        Assertions.assertThrows(BusinessException.class,
                () -> service.list(from, to, null, null, null, null, "not-a-cursor", 2, principal));
    }

    private List<EcomTransactionResponse> rows(int count) {
        List<EcomTransactionResponse> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(new EcomTransactionResponse(
                    "order-" + i, "E1120020", "BazarStore", "ref-" + i,
                    "SUCCESS", "FullyPaid", "Authorized",
                    BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "AZN", "Test",
                    from, from, from.plusSeconds(i), 2,
                    "TERM-1", "401200******7742", "624306104879", null,
                    "payer@example.com", "+994500000000"));
        }
        return rows;
    }
}
