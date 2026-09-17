package az.millikart.ecom.service;

import static az.millikart.ecom.service.TxpgRows.auth;
import static az.millikart.ecom.service.TxpgRows.op;
import static az.millikart.ecom.service.TxpgRows.order;
import static az.millikart.ecom.service.TxpgRows.single;

import az.millikart.ecom.dto.EcomDashboardResponse.CurrencyTotals;
import az.millikart.ecom.dto.EcomDashboardResponse.DailyTotal;
import az.millikart.ecom.repository.TxpgStatementRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// Сводка главной по выписке (Р-91): деньги и статусы — теми же правилами, что страница и /stats выписки,
// плюс разрезы, которых у /stats нет, — сутки в поясе отчёта и терминалы. Суммы в фикстурах условные.
class EcomDashboardAccumulatorTest {

    private static final ZoneId BAKU = ZoneId.of("Asia/Baku");

    // Оплата одним сообщением, холд без списания и частично возвращённая покупка: оплаченных заказов два
    // из трёх, среднее — списанное на оплаченный заказ, выручка — за вычетом возврата.
    @Test
    void totalsCountPaidOrdersAndSubtractRefunds() {
        EcomDashboardAccumulator accumulator = accumulate(
                order("101", "FullyPaid", "Preparing", "10", single("10")),
                order("102", "Closed", "Authorized", "20", auth("20")),
                order("103", "PartPaid", "FullyPaid", "30", single("30"),
                        op("Refund", "Single", null, "Approved", "10", "-10")));

        CurrencyTotals azn = only(accumulator.totals(), "AZN");
        Assertions.assertEquals(3, azn.orderCount());
        Assertions.assertEquals(2, azn.paidCount(), "a hold that was never captured is not a paid order");
        Assertions.assertEquals(new BigDecimal("40.00"), azn.capturedAmount());
        Assertions.assertEquals(new BigDecimal("10.00"), azn.refundedAmount());
        Assertions.assertEquals(new BigDecimal("30.00"), azn.netAmount());
        Assertions.assertEquals(new BigDecimal("20.00"), azn.averagePaidAmount());

        Map<String, Long> statuses = accumulator.statusCounts();
        Assertions.assertEquals(EcomStatusResolver.EcomStatus.values().length, statuses.size(), "every status, zeros too");
        Assertions.assertEquals(1L, statuses.get("SUCCESS"));
        Assertions.assertEquals(1L, statuses.get("CANCELED"));
        Assertions.assertEquals(1L, statuses.get("PARTIALLY_REFUNDED"));
    }

    @Test
    void currenciesAreNeverAddedTogether() {
        EcomDashboardAccumulator accumulator = accumulate(
                order("101", "FullyPaid", "Preparing", "10", "ORDER-101", "AZN", single("10")),
                order("102", "FullyPaid", "Preparing", "5", "ORDER-102", "USD", single("5")));

        List<CurrencyTotals> totals = accumulator.totals();
        Assertions.assertEquals(List.of("AZN", "USD"), totals.stream().map(CurrencyTotals::currency).toList());
        Assertions.assertEquals(new BigDecimal("10.00"), only(totals, "AZN").netAmount());
        Assertions.assertEquals(new BigDecimal("5.00"), only(totals, "USD").netAmount());
    }

    // Сутки — в поясе отчёта: заказ в 23:30 и заказ в 00:30 по Баку — разные дни, хотя в UTC это один день.
    // Все сутки периода присутствуют, пустые — с нулями.
    @Test
    void daysAreCutInTheReportZone_andEmptyDaysArePresent() {
        Instant beforeMidnight = LocalDate.parse("2026-09-01").atTime(23, 30).atZone(BAKU).toInstant();
        Instant afterMidnight = LocalDate.parse("2026-09-02").atTime(0, 30).atZone(BAKU).toInstant();
        EcomDashboardAccumulator accumulator = accumulate(
                createdAt(order("102", "FullyPaid", "Preparing", "20", single("20")), afterMidnight),
                createdAt(order("101", "FullyPaid", "Preparing", "10", single("10")), beforeMidnight));

        Instant from = LocalDate.parse("2026-09-01").atStartOfDay(BAKU).toInstant();
        Instant to = LocalDate.parse("2026-09-04").atStartOfDay(BAKU).toInstant();
        List<DailyTotal> days = accumulator.dailyTotals(from, to);

        Assertions.assertEquals(List.of("2026-09-01", "2026-09-02", "2026-09-03"),
                days.stream().map(day -> day.date().toString()).toList(), "[from, to) — the end is exclusive");
        Assertions.assertEquals(new BigDecimal("10.00"), days.get(0).netAmount());
        Assertions.assertEquals(new BigDecimal("20.00"), days.get(1).netAmount());
        Assertions.assertEquals(new BigDecimal("0.00"), days.get(2).netAmount());
        Assertions.assertEquals(0, days.get(2).orderCount());
    }

    // Пять терминалов с наибольшей выручкой, по убыванию; шестой не попадает. Название — из выписки.
    @Test
    void topTerminalsAreTheFiveWithTheHighestNet() {
        List<List<TxpgStatementRow>> orders = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            orders.add(merchant(order(String.valueOf(100 + i), "FullyPaid", "Preparing", String.valueOf(i * 10),
                    single(String.valueOf(i * 10))), "M-" + i, "Shop " + i));
        }
        EcomDashboardAccumulator accumulator = accumulate(orders.toArray(List[]::new));

        List<EcomDashboardAccumulator.RankedTerminal> top = accumulator.topTerminals();
        Assertions.assertEquals(List.of("M-6", "M-5", "M-4", "M-3", "M-2"),
                top.stream().map(EcomDashboardAccumulator.RankedTerminal::merchantRid).toList());
        Assertions.assertEquals(new BigDecimal("60.00"), top.getFirst().netAmount());
        Assertions.assertEquals("Shop 6", top.getFirst().merchantTitle());
    }

    @Test
    void anEmptyPeriodGivesNoCurrenciesAndZeroForEveryStatus() {
        EcomDashboardAccumulator accumulator = new EcomDashboardAccumulator(BAKU);

        Assertions.assertTrue(accumulator.totals().isEmpty());
        Assertions.assertTrue(accumulator.topTerminals().isEmpty());
        Assertions.assertTrue(accumulator.statusCounts().values().stream().allMatch(count -> count == 0L));
    }

    @SafeVarargs
    private static EcomDashboardAccumulator accumulate(List<TxpgStatementRow>... orders) {
        EcomDashboardAccumulator accumulator = new EcomDashboardAccumulator(BAKU);
        Stream.of(orders).flatMap(List::stream).forEach(accumulator);
        return accumulator;
    }

    private static CurrencyTotals only(List<CurrencyTotals> totals, String currency) {
        return totals.stream().filter(total -> currency.equals(total.currency())).findFirst().orElseThrow();
    }

    private static List<TxpgStatementRow> createdAt(List<TxpgStatementRow> rows, Instant created) {
        return rows.stream().map(r -> new TxpgStatementRow(r.orderId(), r.ridByMerchant(), r.orderStatus(),
                r.orderPrevStatus(), r.description(), r.orderAmount(), r.orderCurrency(), created, r.merchantRid(),
                r.merchantTitle(), r.cardMask(), r.tranId(), r.rrn(), r.tranAt(), r.resultCode(), r.tranAmount(),
                r.clearAmount(), r.tranCurrency(), r.tranType(), r.phase(), r.voidKind(), r.authKind())).toList();
    }

    private static List<TxpgStatementRow> merchant(List<TxpgStatementRow> rows, String rid, String title) {
        return rows.stream().map(r -> new TxpgStatementRow(r.orderId(), r.ridByMerchant(), r.orderStatus(),
                r.orderPrevStatus(), r.description(), r.orderAmount(), r.orderCurrency(), r.orderCreatedAt(), rid,
                title, r.cardMask(), r.tranId(), r.rrn(), r.tranAt(), r.resultCode(), r.tranAmount(),
                r.clearAmount(), r.tranCurrency(), r.tranType(), r.phase(), r.voidKind(), r.authKind())).toList();
    }
}
