package az.millikart.ecom.service;

import static az.millikart.ecom.service.TxpgRows.order;
import static az.millikart.ecom.service.TxpgRows.single;

import az.millikart.ecom.dto.EcomStatsResponse;
import az.millikart.ecom.dto.EcomStatsResponse.CurrencyTotal;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// Итоги периода складываются из потока теми же правилами, что и страница: расхождение «карточки
// статистики против суммы строк» — первое, что мерчант заметит и перестанет верить обоим.
class EcomStatsAccumulatorTest {

    @Test
    void testStandExport_matchesWhatThePageShows() {
        EcomStatsAccumulator accumulator = new EcomStatsAccumulator();
        TxpgRows.testStandExport().forEach(accumulator);

        EcomStatsResponse stats = accumulator.result();

        Assertions.assertEquals(16, stats.orderCount());
        Assertions.assertEquals(10L, stats.statusCounts().get("SUCCESS"));
        Assertions.assertEquals(4L, stats.statusCounts().get("CANCELED"));
        Assertions.assertEquals(2L, stats.statusCounts().get("AUTHORIZED"));
        Assertions.assertEquals(1, stats.totals().size());
        CurrencyTotal azn = stats.totals().get(0);
        Assertions.assertEquals("AZN", azn.currency());
        Assertions.assertEquals(0, new BigDecimal("357").compareTo(azn.capturedAmount()));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(azn.refundedAmount()));
    }

    // Сумма AZN и USD — число без смысла с правильной подписью.
    @Test
    void currenciesAreNeverAddedTogether() {
        EcomStatsAccumulator accumulator = new EcomStatsAccumulator();
        order("175900", "FullyPaid", "Preparing", "10", "ORDER-1", "USD", single("10")).forEach(accumulator);
        order("175901", "FullyPaid", "Preparing", "5", "ORDER-2", "AZN", single("5")).forEach(accumulator);

        EcomStatsResponse stats = accumulator.result();

        Assertions.assertEquals(List.of("AZN", "USD"), stats.totals().stream().map(CurrencyTotal::currency).toList());
        Assertions.assertEquals(0, new BigDecimal("5").compareTo(stats.totals().get(0).capturedAmount()));
        Assertions.assertEquals(0, BigDecimal.TEN.compareTo(stats.totals().get(1).capturedAmount()));
    }

    // Фронтенд рисует карточку на каждый статус: нулевой статус должен прийти нулём, а не пропасть.
    @Test
    void anEmptyPeriod_givesZeroForEveryStatus() {
        EcomStatsResponse stats = new EcomStatsAccumulator().result();

        Assertions.assertEquals(0, stats.orderCount());
        Assertions.assertEquals(List.of("PENDING", "AUTHORIZED", "SUCCESS", "PARTIALLY_PAID", "FAILED",
                "PARTIALLY_REFUNDED", "REFUNDED", "CANCELED"), List.copyOf(stats.statusCounts().keySet()));
        Assertions.assertTrue(stats.statusCounts().values().stream().allMatch(count -> count == 0L));
        Assertions.assertTrue(stats.totals().isEmpty());
    }
}
