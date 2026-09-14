package az.millikart.ecom.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// Итоги периода по всем его заказам, а не по загруженной странице: выписка за квартал ни в одну
// страницу не помещается. Суммы — по валютам: сложить AZN с USD значит показать число без смысла.
public record EcomStatsResponse(
        long orderCount,
        // Все значения EcomStatus по порядку, нулевые тоже.
        Map<String, Long> statusCounts,
        List<CurrencyTotal> totals
) {

    public record CurrencyTotal(String currency, BigDecimal capturedAmount, BigDecimal refundedAmount) {
    }
}
