package az.millikart.ecom.service;

import az.millikart.ecom.repository.TxpgStatementRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Строки шлюза для тестов выписки. Всё, что названо по стенду, — данные выгрузок по запросу провайдера:
// заказы, статусы, фазы и суммы как есть; мерчант, карта и RRN — условные.
public final class TxpgRows {

    public static final String MERCHANT_RID = "M-TEST-1";

    private static final Instant BASE = Instant.parse("2026-08-31T06:00:00Z");

    private TxpgRows() {
    }

    public record Op(String type, String phase, String voidKind, String resultCode, String tranAmount,
                     String clearAmount) {
    }

    public static Op auth(String amount) {
        return new Op("Purchase", "Auth", null, "Approved", amount, "0");
    }

    public static Op clearing(String amount) {
        return new Op("Purchase", "Clearing", null, "Approved", amount, amount);
    }

    public static Op single(String amount) {
        return new Op("Purchase", "Single", null, "Approved", amount, amount);
    }

    public static Op op(String type, String phase, String voidKind, String resultCode, String tranAmount,
                        String clearAmount) {
        return new Op(type, phase, voidKind, resultCode, tranAmount, clearAmount);
    }

    // Операции получают время по порядку, минута за минутой: история заказа читается как шла.
    public static List<TxpgStatementRow> order(String orderId, String status, String prevStatus, String amount,
                                               String ridByMerchant, String currency, Op... ops) {
        Instant created = BASE.plusSeconds(Long.parseLong(orderId) * 60);
        List<TxpgStatementRow> rows = new ArrayList<>();
        for (int i = 0; i < ops.length; i++) {
            Op op = ops[i];
            rows.add(new TxpgStatementRow(
                    orderId, ridByMerchant, status, prevStatus, "test order", new BigDecimal(amount), currency,
                    created, MERCHANT_RID, "Test Shop", "401200******7742",
                    "2609" + orderId + i, "6243" + orderId, created.plusSeconds(60L * (i + 1)),
                    op.resultCode(), new BigDecimal(op.tranAmount()),
                    op.clearAmount() != null ? new BigDecimal(op.clearAmount()) : null, currency,
                    op.type(), op.phase(), op.voidKind(), authKind(op)));
        }
        return rows;
    }

    // Как на стенде: у авторизации Preliminary, у реверсала Undefined, у остальных пусто.
    private static String authKind(Op op) {
        if (op.voidKind() != null) {
            return "Undefined";
        }
        return "Auth".equals(op.phase()) ? "Preliminary" : null;
    }

    public static List<TxpgStatementRow> order(String orderId, String status, String prevStatus, String amount,
                                               Op... ops) {
        return order(orderId, status, prevStatus, amount, "ORDER-" + orderId, "AZN", ops);
    }

    // Заказ 175195 со стенда (выгрузка 14.09.2026): холд 50, снято 20, списано 30, возвращено тремя
    // частями с минусом в clearamt. До финального Refused заказ был Authorized.
    public static List<TxpgStatementRow> refundedAfterPartialClearing() {
        return order("175195", "Refused", "Authorized", "50", null, "AZN",
                auth("50"),
                op("Purchase", "Auth", "Partial", "Approved", "20", "0"),
                clearing("30"),
                op("Refund", "Single", null, "Approved", "5", "-5"),
                op("Refund", "Single", null, "Approved", "20", "-20"),
                op("Refund", "Single", null, "Approved", "5", "-5"));
    }

    // По заказу на каждое сочетание «статус ← предыдущий статус» из выгрузки стенда от 14.09.2026
    // (114 заказов, 188 операций). Реверсалы, возвраты, отказы и частичная оплата — строки как есть.
    public static List<TxpgStatementRow> providerStatusesSeenOnTheStand() {
        List<TxpgStatementRow> rows = new ArrayList<>();
        rows.addAll(order("176059", "PartPaid", "Preparing", "16", single("10")));
        rows.addAll(order("176001", "FullyPaid", "Preparing", "6", single("6")));
        rows.addAll(order("175999", "Expired", "Preparing", "6", op("Purchase", "Single", null, null, "6", "0")));
        rows.addAll(order("175897", "Rejected", "Preparing", "10",
                op("Purchase", "Single", null, "InvalidRequest", "10", "0")));
        rows.addAll(order("175700", "Closed", "Authorized", "10", auth("10")));
        rows.addAll(order("175662", "Closed", "FullyPaid", "22", auth("22"), clearing("22")));
        rows.addAll(order("175316", "Closed", "PartPaid", "22", auth("22"), clearing("22"),
                op("Refund", "Single", null, "Approved", "7", "-7")));
        rows.addAll(order("175247", "Closed", "PartPaid", "22", auth("22"),
                op("Purchase", "Auth", "Partial", "Approved", "2", "0"), clearing("20")));
        rows.addAll(order("175203", "Refused", "Authorized", "120", auth("120"),
                op("Purchase", "Auth", "Partial", "Approved", "20", "0"), clearing("100"),
                op("Refund", "Single", null, "Approved", "50", "-50"),
                op("Refund", "Single", null, "Approved", "50", "-50")));
        rows.addAll(order("175204", "Cancelled", "Preparing", "120", auth("120"),
                op("Purchase", "Auth", "Full", "Approved", "120", "0")));
        rows.addAll(order("175164", "Cancelled", "Preparing", ".01", single(".01"),
                op("Purchase", "Single", "Full", "Approved", ".01", "-.01")));
        rows.addAll(order("175378", "Rejected", "Authorized", "120", auth("120")));
        rows.addAll(order("175246", "Rejected", "Refused", "12", auth("12"), clearing("12"),
                op("Refund", "Single", null, "Approved", "12", "-12")));
        return rows;
    }

    // 16 заказов, 25 операций. Списано 357 AZN; прежний запрос насчитал бы 770.
    public static List<TxpgStatementRow> testStandExport() {
        List<TxpgStatementRow> rows = new ArrayList<>();
        rows.addAll(order("175700", "Authorized", "Preparing", "10", auth("10")));
        rows.addAll(order("175697", "Authorized", "Preparing", "10", auth("10")));
        rows.addAll(order("175675", "Closed", "Authorized", "20", auth("20")));
        rows.addAll(order("175670", "Closed", "Authorized", "20", auth("20")));
        rows.addAll(order("175662", "FullyPaid", "Authorized", "22", auth("22"), clearing("22")));
        rows.addAll(order("175661", "FullyPaid", "Authorized", "20", auth("20"), clearing("20")));
        rows.addAll(order("175605", "Closed", "Authorized", "1", null, "AZN", auth("1")));
        rows.addAll(order("175604", "Closed", "Authorized", "20", auth("20")));
        rows.addAll(order("175533", "Closed", "FullyPaid", "10", auth("10"), clearing("10"), clearing("5")));
        rows.addAll(order("175531", "Closed", "FullyPaid", "10", auth("10"), clearing("10")));
        rows.addAll(order("175530", "Closed", "FullyPaid", "10", auth("10"), clearing("10")));
        rows.addAll(order("175529", "Closed", "FullyPaid", "10", single("10")));
        rows.addAll(order("175528", "Closed", "FullyPaid", "10", single("10")));
        rows.addAll(order("175525", "Closed", "FullyPaid", "120", auth("120"), clearing("120")));
        rows.addAll(order("175523", "Closed", "FullyPaid", "120", auth("120"), clearing("120")));
        rows.addAll(order("175522", "Closed", "FullyPaid", "20", auth("20"), clearing("20")));
        return rows;
    }
}
