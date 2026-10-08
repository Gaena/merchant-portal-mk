package az.millikart.pbl.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// Статистика оплат по ссылкам (P3-7, Р-91). Деньги — всегда с валютой: сводный итог поверх валют
// был бы числом, которого не существует.
public record DashboardSummaryResponse(
        Window window,
        List<CurrencyTotals> totals,
        List<StatusCount> statusBreakdown,
        List<DailyTotal> dailyTotals,
        List<HourlyCount> hourlyTotals,
        List<TerminalTotal> topTerminals,
        PaymentLinkTotals paymentLinks,
        LinkFunnel linkFunnel,
        TimeToPay timeToPay
) {

    // zone — чтобы подпись пояса на экране бралась из ответа: сутки и часы режет сервер.
    public record Window(Instant from, Instant to, String zone) {
    }

    // paidCount/paidAmount — по TransactionStatus.PAID_STATUSES: возвращённый платёж деньги
    // получал, и из выручки он вычитается возвратом, а не выпадает целиком.
    public record CurrencyTotals(
            String currency,
            long transactionCount,
            long paidCount,
            long failedCount,
            long pendingCount,
            long refundedCount,
            BigDecimal paidAmount,
            BigDecimal refundedAmount,
            BigDecimal netAmount,
            BigDecimal averagePaidAmount
    ) {
    }

    // Все значения TransactionStatus, включая нулевые: пропуск читался бы как «не бывает», а не «не было».
    public record StatusCount(String status, long count) {
    }

    public record DailyTotal(LocalDate date, String currency, BigDecimal netAmount, long transactionCount) {
    }

    public record HourlyCount(int hour, long transactionCount) {
    }

    // terminalRid — номер терминала у провайдера, основная подпись (Р-96); без него — логин. Всё null,
    // если терминала в таблице уже нет: выдумывать подпись по id нельзя.
    public record TerminalTotal(
            String currency,
            Integer terminalId,
            String terminalRid,
            String terminalLogin,
            String terminalName,
            BigDecimal netAmount,
            long transactionCount
    ) {
    }

    public record PaymentLinkTotals(
            long total,
            List<PaymentTypeCount> byPaymentType,
            List<UsageTypeCount> byUsageType,
            List<LinkStatusCount> byStatus
    ) {
    }

    public record PaymentTypeCount(String paymentType, long count) {
    }

    public record UsageTypeCount(String usageType, long count) {
    }

    public record LinkStatusCount(String status, long count) {
    }

    // Р-128: ссылки, созданные в окне, — когорта: шаги считают их попытки и после окна, и недавний период
    // дорастает. Оплачена — списание или холд: плательщик свою часть сделал.
    public record LinkFunnel(long created, long opened, long paymentStarted, long paid) {
    }

    // Р-128: только одноразовые ссылки окна, от создания ссылки до начала оплаченной попытки.
    // medianSeconds — null, когда оплаченных нет: ноль читался бы как «платят мгновенно».
    public record TimeToPay(long paidLinks, Long medianSeconds, List<TimeToPayBucket> buckets) {
    }

    // Все четыре интервала, включая нулевые, по порядку: UP_TO_1_HOUR, UP_TO_1_DAY, UP_TO_7_DAYS, OVER_7_DAYS.
    public record TimeToPayBucket(String range, long count) {
    }
}
