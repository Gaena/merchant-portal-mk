package az.millikart.pbl.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// Сводка главной страницы (P3-7). Считает база, фронтенд только рисует.
// Денег без валюты здесь нет ни в одном поле: колонки currency у transactions не существует,
// она на payment_links, и туда попадает любой трёхбуквенный код — сводный итог поверх нескольких
// валют был бы числом, которого не существует.
public record DashboardSummaryResponse(
        Window window,
        List<CurrencyTotals> totals,
        List<StatusCount> statusBreakdown,
        List<DailyTotal> dailyTotals,
        List<HourlyCount> hourlyTotals,
        List<TerminalTotal> topTerminals,
        PaymentLinkTotals paymentLinks
) {

    // zone возвращается, чтобы подпись «время бакинское» на экране бралась из ответа, а не
    // выдумывалась фронтендом: сутки и часы режет сервер, ему и объясняться.
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

    // Все шесть значений TransactionStatus, включая нулевые: пропущенная доля читается как
    // «такого не бывает», а не как «за окно не случилось».
    public record StatusCount(String status, long count) {
    }

    public record DailyTotal(LocalDate date, String currency, BigDecimal netAmount, long transactionCount) {
    }

    public record HourlyCount(int hour, long transactionCount) {
    }

    public record TerminalTotal(
            String currency,
            Integer terminalId,
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
}
