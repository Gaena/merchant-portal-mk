package az.millikart.ecom.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.dto.EcomTransactionFilter;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

// Что уходит в Oracle провайдера. Сам SQL здесь не исполняется — их схему с пакетом RDX_Action
// не поднять ни в каком контейнере. Проверяется то, чья цена — чужие платежи на экране или
// упавшая выписка: скоуп и Р-71 в каждом запросе, пояс дат, колонки только из запроса провайдера.
@SuppressWarnings("unchecked")
class TxpgTransactionRepositoryTest {

    private static final List<String> LOGINS = List.of("BS00001", "BS00002");
    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");

    private NamedParameterJdbcTemplate jdbc;
    private TxpgTransactionRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        repository = new TxpgTransactionRepository(jdbc, new TxpgProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // Скоуп — по запросу выписки от 15.09.2026: мерчанты логинов TerminalSys, и операция того же
    // мерчанта, что и заказ. Без фильтра по терминалу сужения по merchant.rid нет.
    @Test
    void everyQueryCarriesTheLoginScopeAndTheFinishedOrdersRule() {
        EcomTransactionFilter filter = filter(Instant.parse("2026-09-01T00:00:00Z"), NOW);

        repository.findOrderIds(filter, null, 26);
        repository.findRows(List.of(175533L), LOGINS, filter.dateFrom());
        repository.streamPeriodRows(filter, row -> { });

        List<Captured> queries = capturedQueries();
        Assertions.assertEquals(3, queries.size());
        for (Captured query : queries) {
            String sql = query.sql().replaceAll("\\s+", " ");
            Assertions.assertTrue(sql.contains("and tr.merchantid in (select l.merchantid from TXPG.login l "
                    + "where l.ownerkind = 'TerminalSys' and l.login in (:logins))"), sql);
            Assertions.assertTrue(sql.contains("join TXPG.merchant m on m.id = o.merchantid and m.id = tr.merchantid"), sql);
            Assertions.assertFalse(sql.contains(":merchant_rids"), sql);
            Assertions.assertTrue(sql.contains("o.status not in (:unfinished_statuses)"), sql);
            // Р-76: исключение для Authorized со списанием — во всех трёх, иначе итоги разойдутся со страницей.
            Assertions.assertTrue(sql.contains("o.status = 'Authorized'"), sql);
            Assertions.assertEquals(LOGINS, query.params().getValue("logins"));
            Assertions.assertEquals(List.of("Preparing", "Authorized", "Expired"),
                    query.params().getValue("unfinished_statuses"));
        }
    }

    // Фильтр по терминалу сужает выбор заказов и в итогах, и на странице; скоуп по логинам остаётся.
    @Test
    void theTerminalFilterNarrowsTheOrdersOnTopOfTheLoginScope() {
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        EcomTransactionFilter narrowed = new EcomTransactionFilter(LOGINS, List.of("M-1"), from, NOW, null, null, null);

        repository.findOrderIds(narrowed, null, 26);
        repository.streamPeriodRows(narrowed, row -> { });

        for (Captured query : capturedQueries()) {
            Assertions.assertTrue(query.sql().contains("and m.rid in (:merchant_rids)"), query.sql());
            Assertions.assertTrue(query.sql().contains("l.login in (:logins)"), query.sql());
            Assertions.assertEquals(List.of("M-1"), query.params().getValue("merchant_rids"));
            Assertions.assertEquals(LOGINS, query.params().getValue("logins"));
        }
    }

    // Одна отсутствующая в схеме колонка роняет всю выписку (ORA-00904). Читаем только то, что есть
    // в SQL провайдера; пароль заказа не выбирается никогда.
    @Test
    void onlyColumnsFromTheProviderQueryAreRead() {
        EcomTransactionFilter filter = filter(Instant.parse("2026-09-01T00:00:00Z"), NOW);

        repository.findOrderIds(new EcomTransactionFilter(LOGINS, List.of("M-1"), filter.dateFrom(), filter.dateTo(),
                BigDecimal.ONE, BigDecimal.TEN, "175533"), 175600L, 26);
        repository.findRows(List.of(175533L), LOGINS, null);
        repository.streamPeriodRows(filter, row -> { });

        for (Captured query : capturedQueries()) {
            String sql = query.sql().toLowerCase();
            for (String unconfirmed : List.of("terminalid", "ridbypmo", "srcemail", "srcmobile",
                    "gethighidfortime", "password")) {
                Assertions.assertFalse(sql.contains(unconfirmed), unconfirmed + " in " + query.sql());
            }
        }
    }

    // Р-76: при мультиклиринге заказ после списания остаётся Authorized. Скрыт только Authorized без
    // одобренного списания — признак тот же, что у EcomOperationKind.CAPTURE.
    @Test
    void anAuthorizedOrderIsHiddenOnlyUntilSomethingIsCaptured() {
        repository.findOrderIds(filter(Instant.parse("2026-09-01T00:00:00Z"), NOW), null, 26);

        String sql = capturedQueries().get(0).sql().replaceAll("\\s+", " ");
        Assertions.assertTrue(sql.contains("and (o.status not in (:unfinished_statuses) or (o.status = 'Authorized' "
                + "and exists (select 1 from TXPG.tran c where c.orderid = o.id and c.trantype = 'Purchase' "
                + "and c.phase = 'Clearing' and c.voidkind is null and c.pmoresultcode = 'Approved')))"), sql);
    }

    // Даты шлюза — местное время Баку без пояса: полночь по Баку — это 20:00 UTC накануне.
    @Test
    void periodBoundsAreSentAsLocalTimeOfTheGatewayZone() {
        repository.findOrderIds(filter(Instant.parse("2026-08-31T20:00:00Z"), Instant.parse("2026-09-01T20:00:00Z")),
                null, 26);

        MapSqlParameterSource params = capturedQueries().get(0).params();
        Assertions.assertEquals(LocalDateTime.parse("2026-09-01T00:00"), params.getValue("date_from"));
        Assertions.assertEquals(LocalDateTime.parse("2026-09-02T00:00"), params.getValue("date_to"));
    }

    @Test
    void gatewayTimesAreReadAsLocalTimeOfTheGatewayZone() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("order_id")).thenReturn("175700");
        when(rs.getTimestamp("tran_time")).thenReturn(Timestamp.valueOf(LocalDateTime.parse("2026-09-04T11:14:49")));
        doAnswer(invocation -> {
            invocation.getArgument(2, RowCallbackHandler.class).processRow(rs);
            return null;
        }).when(jdbc).query(anyString(), any(SqlParameterSource.class), any(RowCallbackHandler.class));
        List<TxpgStatementRow> rows = new ArrayList<>();

        repository.streamPeriodRows(filter(Instant.parse("2026-09-01T00:00:00Z"), NOW), rows::add);

        Assertions.assertEquals(Instant.parse("2026-09-04T07:14:49Z"), rows.get(0).tranAt());
        Assertions.assertNull(rows.get(0).orderCreatedAt());
    }

    // У прошедшего периода скан по tran.id ограничен сверху; у текущего — нет: там граница упёрлась
    // бы в часы базы и отрезала платежи последних минут.
    @Test
    void aPastPeriodIsBoundedFromAbove_theCurrentOneIsNot() {
        repository.findOrderIds(filter(Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z")),
                null, 26);
        repository.findOrderIds(filter(Instant.parse("2026-09-01T00:00:00Z"), NOW), null, 26);

        List<Captured> queries = capturedQueries();
        Assertions.assertTrue(queries.get(0).sql().contains(":scan_to"));
        Assertions.assertEquals(LocalDateTime.parse("2026-08-02T04:00"), queries.get(0).params().getValue("scan_to"));
        Assertions.assertFalse(queries.get(1).sql().contains(":scan_to"));
    }

    // На будущем времени getLowIdForTime не работает (провайдер, 14.09.2026). Ни в одном запросе
    // функция не получает время мимо часов самой базы — даже когда весь период в будущем.
    @Test
    void theIdWindowFunctionNeverGetsTimeBeyondTheDatabaseClock() {
        EcomTransactionFilter future = filter(NOW.plus(1, ChronoUnit.DAYS), NOW.plus(2, ChronoUnit.DAYS));

        repository.findOrderIds(future, null, 26);
        repository.findRows(List.of(175533L), LOGINS, future.dateFrom());
        repository.streamPeriodRows(future, row -> { });
        repository.findOrderIds(filter(Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z")),
                null, 26);

        List<Captured> queries = capturedQueries();
        Assertions.assertEquals(4, queries.size());
        for (Captured query : queries) {
            int calls = occurrences(query.sql(), "getLowIdForTime(");
            Assertions.assertTrue(calls > 0, query.sql());
            Assertions.assertEquals(calls, occurrences(query.sql(), "getLowIdForTime(least(cast(:"), query.sql());
            Assertions.assertEquals(calls, occurrences(query.sql(), "sysdate - interval '5' minute)"), query.sql());
        }
    }

    @Test
    void theNextPageStartsBelowTheLastOrderOfThePreviousOne() {
        repository.findOrderIds(filter(Instant.parse("2026-09-01T00:00:00Z"), NOW), 175600L, 26);

        Captured query = capturedQueries().get(0);
        Assertions.assertTrue(query.sql().contains("o.id < :before_order_id"));
        Assertions.assertEquals(175600L, query.params().getValue("before_order_id"));
        Assertions.assertEquals(26, query.params().getValue("limit"));
        Assertions.assertTrue(query.sql().contains("order by o.id desc"));
    }

    // Карточка открывается по номеру без периода: окна по операциям у неё нет, история полная.
    @Test
    void theOrderCardReadsTheWholeHistory() {
        repository.findRows(List.of(175533L), LOGINS, null);

        Assertions.assertFalse(capturedQueries().get(0).sql().contains(":operations_from"));
    }

    @Test
    void anEmptyPage_doesNotGoToTheGateway() {
        Assertions.assertTrue(repository.findRows(List.of(), LOGINS, NOW).isEmpty());
        verifyNoInteractions(jdbc);
    }

    private static EcomTransactionFilter filter(Instant from, Instant to) {
        return new EcomTransactionFilter(LOGINS, null, from, to, null, null, null);
    }

    private record Captured(String sql, MapSqlParameterSource params) {
    }

    private static int occurrences(String text, String fragment) {
        int count = 0;
        for (int at = text.indexOf(fragment); at >= 0; at = text.indexOf(fragment, at + 1)) {
            count++;
        }
        return count;
    }

    // Сначала запросы с RowMapper в порядке вызова, затем потоковые.
    private List<Captured> capturedQueries() {
        List<Captured> captured = new ArrayList<>();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc, atLeast(0)).query(sql.capture(), params.capture(), any(RowMapper.class));
        verify(jdbc, atLeast(0)).query(sql.capture(), params.capture(), any(RowCallbackHandler.class));
        for (int i = 0; i < sql.getAllValues().size(); i++) {
            captured.add(new Captured(sql.getAllValues().get(i), (MapSqlParameterSource) params.getAllValues().get(i)));
        }
        return captured;
    }
}
