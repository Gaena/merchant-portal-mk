package az.millikart.ecom.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Сводка главной страницы (Р-91): оплаты картой по всем терминалам скоупа — по выписке провайдера, а
// не по платёжным ссылкам портала. Заказы периода и деньги заказа — те же, что во вкладке E-commerce и в
// её /stats (Р-74…Р-78): период — по дате создания заказа. Суммы — по валютам, общего итога нет.
public record EcomDashboardResponse(
        Window window,
        List<CurrencyTotals> totals,
        // Все значения EcomStatus по порядку, нулевые тоже.
        Map<String, Long> statusCounts,
        // Каждые сутки периода в поясе ecom.txpg.zone для каждой валюты, пустые — с нулями.
        List<DailyTotal> dailyTotals,
        // Пять терминалов с наибольшей выручкой внутри каждой валюты.
        List<TerminalTotal> topTerminals
) {

    public record Window(Instant from, Instant to, String zone) {
    }

    // paidCount — заказы, по которым что-то списано (в том числе позже возвращённые); среднее — списанное
    // на один такой заказ. netAmount = capturedAmount − refundedAmount.
    public record CurrencyTotals(String currency, long orderCount, long paidCount, BigDecimal capturedAmount,
                                 BigDecimal refundedAmount, BigDecimal netAmount, BigDecimal averagePaidAmount) {
    }

    public record DailyTotal(LocalDate date, String currency, BigDecimal netAmount, long orderCount) {
    }

    // login и title — из слепка терминалов провайдера; нет в слепке — название мерчанта из выписки, логина нет.
    public record TerminalTotal(String currency, String merchantRid, String login, String title,
                                BigDecimal netAmount, long orderCount) {
    }
}
