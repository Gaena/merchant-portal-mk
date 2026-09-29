package az.millikart.ecom.service;

import az.millikart.ecom.dto.EcomDashboardResponse.CurrencyTotals;
import az.millikart.ecom.dto.EcomDashboardResponse.DailyTotal;
import az.millikart.ecom.repository.TxpgStatementRow;
import az.millikart.ecom.service.EcomOrderAssembler.OrderMoney;
import az.millikart.ecom.service.EcomStatusResolver.EcomStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

// Строки одного заказа идут подряд (поток сортируется по o.id), иначе заказ посчитается дважды. Деньги и
// статус — EcomOrderAssembler.money: так цифры главной сходятся с вкладкой E-commerce (Р-91).
final class EcomDashboardAccumulator implements Consumer<TxpgStatementRow> {

    static final int TOP_TERMINALS = 5;
    private static final int MONEY_SCALE = 2;

    private final ZoneId zone;
    private final List<TxpgStatementRow> currentOrder = new ArrayList<>();
    private final Map<String, Long> statusCounts = new LinkedHashMap<>();
    private final Map<String, Totals> perCurrency = new LinkedHashMap<>();
    private final Map<DayKey, Totals> perDay = new LinkedHashMap<>();
    private final Map<TerminalKey, Totals> perTerminal = new LinkedHashMap<>();
    private final Map<String, String> merchantTitles = new LinkedHashMap<>();

    EcomDashboardAccumulator(ZoneId zone) {
        this.zone = zone;
        for (EcomStatus status : EcomStatus.values()) {
            statusCounts.put(status.name(), 0L);
        }
    }

    @Override
    public void accept(TxpgStatementRow row) {
        if (!currentOrder.isEmpty() && !currentOrder.getFirst().orderId().equals(row.orderId())) {
            closeOrder();
        }
        currentOrder.add(row);
    }

    Map<String, Long> statusCounts() {
        closeOrder();
        return Collections.unmodifiableMap(new LinkedHashMap<>(statusCounts));
    }

    List<CurrencyTotals> totals() {
        closeOrder();
        List<CurrencyTotals> result = new ArrayList<>();
        perCurrency.forEach((currency, totals) -> result.add(new CurrencyTotals(currency, totals.orders, totals.paid,
                money(totals.captured), money(totals.refunded), money(totals.net()), totals.average())));
        result.sort(Comparator.comparing(CurrencyTotals::currency, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    // Все сутки [from, to) в поясе отчёта, по каждой валюте периода: пропуск дня читался бы как «данных нет».
    List<DailyTotal> dailyTotals(Instant from, Instant to) {
        closeOrder();
        LocalDate first = from.atZone(zone).toLocalDate();
        LocalDate last = to.minusNanos(1).atZone(zone).toLocalDate();
        List<DailyTotal> result = new ArrayList<>();
        perCurrency.keySet().stream()
                .sorted(Comparator.nullsLast(Comparator.naturalOrder()))
                .forEach(currency -> {
                    for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
                        Totals totals = perDay.get(new DayKey(date, currency));
                        result.add(new DailyTotal(date, currency,
                                totals == null ? money(BigDecimal.ZERO) : money(totals.net()),
                                totals == null ? 0L : totals.orders));
                    }
                });
        return result;
    }

    // Топ по выручке внутри каждой валюты: сравнивать манаты с евро нельзя.
    List<RankedTerminal> topTerminals() {
        closeOrder();
        Map<String, List<Map.Entry<TerminalKey, Totals>>> byCurrency = new LinkedHashMap<>();
        perTerminal.entrySet().forEach(entry ->
                byCurrency.computeIfAbsent(entry.getKey().currency(), key -> new ArrayList<>()).add(entry));
        List<RankedTerminal> result = new ArrayList<>();
        byCurrency.keySet().stream()
                .sorted(Comparator.nullsLast(Comparator.naturalOrder()))
                .forEach(currency -> byCurrency.get(currency).stream()
                        .sorted(Comparator.<Map.Entry<TerminalKey, Totals>, BigDecimal>comparing(e -> e.getValue().net())
                                .reversed()
                                .thenComparing(e -> e.getKey().merchantRid(), Comparator.nullsLast(Comparator.naturalOrder())))
                        .limit(TOP_TERMINALS)
                        .forEach(e -> result.add(new RankedTerminal(currency, e.getKey().merchantRid(),
                                merchantTitles.get(e.getKey().merchantRid()), money(e.getValue().net()),
                                e.getValue().orders))));
        return result;
    }

    record RankedTerminal(String currency, String merchantRid, String merchantTitle, BigDecimal netAmount,
                          long orderCount) {
    }

    private void closeOrder() {
        if (currentOrder.isEmpty()) {
            return;
        }
        TxpgStatementRow head = currentOrder.getFirst();
        OrderMoney money = EcomOrderAssembler.money(currentOrder);
        String currency = head.orderCurrency();
        statusCounts.merge(money.status().name(), 1L, Long::sum);
        perCurrency.computeIfAbsent(currency, key -> new Totals()).add(money);
        if (head.orderCreatedAt() != null) {
            perDay.computeIfAbsent(new DayKey(head.orderCreatedAt().atZone(zone).toLocalDate(), currency),
                    key -> new Totals()).add(money);
        }
        perTerminal.computeIfAbsent(new TerminalKey(head.merchantRid(), currency), key -> new Totals()).add(money);
        if (head.merchantRid() != null && head.merchantTitle() != null) {
            merchantTitles.putIfAbsent(head.merchantRid(), head.merchantTitle());
        }
        currentOrder.clear();
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private record DayKey(LocalDate date, String currency) {
        DayKey {
            Objects.requireNonNull(date);
        }
    }

    private record TerminalKey(String merchantRid, String currency) {
    }

    private static final class Totals {
        private long orders;
        private long paid;
        private BigDecimal captured = BigDecimal.ZERO;
        private BigDecimal refunded = BigDecimal.ZERO;

        private void add(OrderMoney money) {
            orders++;
            if (money.captured().signum() > 0) {
                paid++;
            }
            captured = captured.add(money.captured());
            refunded = refunded.add(money.refunded());
        }

        private BigDecimal net() {
            return captured.subtract(refunded);
        }

        private BigDecimal average() {
            return paid == 0
                    ? money(BigDecimal.ZERO)
                    : captured.divide(BigDecimal.valueOf(paid), MONEY_SCALE, RoundingMode.HALF_UP);
        }
    }
}
