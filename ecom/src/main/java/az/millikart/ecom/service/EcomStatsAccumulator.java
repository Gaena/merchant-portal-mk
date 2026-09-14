package az.millikart.ecom.service;

import az.millikart.ecom.dto.EcomStatsResponse;
import az.millikart.ecom.dto.EcomStatsResponse.CurrencyTotal;
import az.millikart.ecom.repository.TxpgStatementRow;
import az.millikart.ecom.service.EcomOrderAssembler.OrderMoney;
import az.millikart.ecom.service.EcomStatusResolver.EcomStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// Итоги периода из потока строк. Строки одного заказа обязаны идти подряд — поток сортируется по
// o.id: иначе заказ посчитается дважды. Деньги — EcomOrderAssembler.money, как у страницы.
final class EcomStatsAccumulator implements Consumer<TxpgStatementRow> {

    private final List<TxpgStatementRow> currentOrder = new ArrayList<>();
    private final Map<String, Long> statusCounts = new LinkedHashMap<>();
    private final Map<String, BigDecimal> capturedByCurrency = new LinkedHashMap<>();
    private final Map<String, BigDecimal> refundedByCurrency = new LinkedHashMap<>();
    private long orderCount;

    EcomStatsAccumulator() {
        for (EcomStatus status : EcomStatus.values()) {
            statusCounts.put(status.name(), 0L);
        }
    }

    @Override
    public void accept(TxpgStatementRow row) {
        if (!currentOrder.isEmpty() && !currentOrder.get(0).orderId().equals(row.orderId())) {
            closeOrder();
        }
        currentOrder.add(row);
    }

    EcomStatsResponse result() {
        closeOrder();
        List<CurrencyTotal> totals = new ArrayList<>();
        capturedByCurrency.forEach((currency, captured) ->
                totals.add(new CurrencyTotal(currency, captured, refundedByCurrency.get(currency))));
        totals.sort(Comparator.comparing(CurrencyTotal::currency, Comparator.nullsLast(Comparator.naturalOrder())));
        return new EcomStatsResponse(orderCount,
                Collections.unmodifiableMap(new LinkedHashMap<>(statusCounts)), List.copyOf(totals));
    }

    private void closeOrder() {
        if (currentOrder.isEmpty()) {
            return;
        }
        OrderMoney money = EcomOrderAssembler.money(currentOrder);
        String currency = currentOrder.get(0).orderCurrency();
        orderCount++;
        statusCounts.merge(money.status().name(), 1L, Long::sum);
        capturedByCurrency.merge(currency, money.captured(), BigDecimal::add);
        refundedByCurrency.merge(currency, money.refunded(), BigDecimal::add);
        currentOrder.clear();
    }
}
