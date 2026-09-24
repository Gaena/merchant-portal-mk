package az.millikart.ecom.service;

import static az.millikart.ecom.service.TxpgRows.auth;
import static az.millikart.ecom.service.TxpgRows.clearing;
import static az.millikart.ecom.service.TxpgRows.op;
import static az.millikart.ecom.service.TxpgRows.order;
import static az.millikart.ecom.service.TxpgRows.single;

import az.millikart.ecom.dto.EcomOperationResponse;
import az.millikart.ecom.dto.EcomTransactionResponse;
import az.millikart.ecom.repository.TxpgStatementRow;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// Склейка строк шлюза в заказы и деньги заказа (Р-74, Р-75, Р-77, Р-78). Главный тест — на выгрузке стенда:
// прежний запрос складывал tranamt всех Purchase и насчитал бы 770 AZN вместо 357, а холды без
// списания показывал успешными.
class EcomOrderAssemblerTest {

    @Test
    void wholeTestStandExport_capturesWhatWasCleared_notEveryPurchaseRow() {
        List<EcomTransactionResponse> orders = EcomOrderAssembler.assemble(TxpgRows.testStandExport());

        Assertions.assertEquals(16, orders.size());
        BigDecimal captured = orders.stream().map(EcomTransactionResponse::capturedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Assertions.assertEquals(0, new BigDecimal("357").compareTo(captured));
        Map<String, Long> statuses = orders.stream()
                .collect(Collectors.groupingBy(EcomTransactionResponse::status, Collectors.counting()));
        Assertions.assertEquals(Map.of("SUCCESS", 10L, "CANCELED", 4L, "AUTHORIZED", 2L), statuses);
    }

    // DMS — две строки шлюза, одна строка выписки: авторизация денег не списывает, списание одно.
    @Test
    void dmsPayment_isOneOrderWithBothOperations_andCountsTheClearingOnce() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(
                order("175662", "FullyPaid", "Authorized", "22", auth("22"), clearing("22"))));

        Assertions.assertEquals("SUCCESS", order.status());
        Assertions.assertEquals(0, new BigDecimal("22").compareTo(order.capturedAmount()));
        Assertions.assertEquals(List.of("AUTHORIZATION", "CAPTURE"),
                order.operations().stream().map(EcomOperationResponse::kind).toList());
        Assertions.assertEquals(order.operations().get(1).at(), order.lastOperationAt());
    }

    // Р-86: на контуре из test.env DMS пишется своими типами — Authorization/Auth и Capture/Charge
    // (заказ 1003, лог ecom 16.09.2026). Типы и фазы — оттуда; статус заказа и суммы условные: строк
    // заказа целиком не было. До правила обе операции были UNKNOWN, заказ выходил PENDING и 0 списано.
    @Test
    void dmsPaymentWrittenAsAuthorizationAndCapture_isReadLikeAuthAndClearing() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(
                order("1003", "FullyPaid", "Authorized", "22",
                        op("Authorization", "Auth", null, "Approved", "22", "0"),
                        op("Capture", "Charge", null, "Approved", "22", "22"))));

        Assertions.assertEquals("SUCCESS", order.status());
        Assertions.assertEquals(0, new BigDecimal("22").compareTo(order.capturedAmount()));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(order.refundedAmount()));
        Assertions.assertEquals(List.of("AUTHORIZATION", "CAPTURE"),
                order.operations().stream().map(EcomOperationResponse::kind).toList());
    }

    // Холд в новом словаре, который провайдер ещё не списал, читается так же, как Purchase/Auth.
    @Test
    void holdWrittenAsAuthorization_theMerchantCanStillCapture_isAuthorized() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(
                order("1004", "Authorized", "Preparing", "15",
                        op("Authorization", "Auth", null, "Approved", "15", "0"))));

        Assertions.assertEquals("AUTHORIZED", order.status());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(order.capturedAmount()));
    }

    // Пустая фаза у операции с типом нового словаря — не списание и не холд, а UNKNOWN. Раньше здесь
    // стоял row.phase().equals(...), и одна такая строка роняла NullPointerException всю выписку.
    // Статус — FullyPaid провайдера (Р-92), хотя в деньги не вошло ничего.
    @Test
    void operationWithoutPhase_isUnknown_andDoesNotBreakTheStatement() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(
                order("1005", "FullyPaid", "Authorized", "10",
                        op("Capture", null, null, "Approved", "10", "10"),
                        op("Authorization", null, null, "Approved", "10", "0"))));

        Assertions.assertEquals(List.of("UNKNOWN", "UNKNOWN"),
                order.operations().stream().map(EcomOperationResponse::kind).toList());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(order.capturedAmount()));
        Assertions.assertEquals("SUCCESS", order.status());
    }

    @Test
    void singleMessagePayment_isSuccess() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(
                order("175529", "Closed", "FullyPaid", "10", single("10"))));

        Assertions.assertEquals("SUCCESS", order.status());
        Assertions.assertEquals(0, BigDecimal.TEN.compareTo(order.capturedAmount()));
        Assertions.assertEquals("PURCHASE", order.operations().get(0).kind());
    }

    // Closed после Authorized — провайдер сам снял холд, по которому не было списания. Прежний
    // запрос показывал такой заказ успешным и с деньгами.
    @Test
    void holdClosedByTheProvider_isCanceled_andCapturesNothing() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(
                order("175670", "Closed", "Authorized", "20", auth("20"))));

        Assertions.assertEquals("CANCELED", order.status());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(order.capturedAmount()));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(order.refundedAmount()));
    }

    @Test
    void holdTheMerchantCanStillCapture_isAuthorized() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(
                order("175700", "Authorized", "Preparing", "10", auth("10"))));

        Assertions.assertEquals("AUTHORIZED", order.status());
    }

    // Мультиклиринг (провайдер, 14.09.2026): по одной авторизации несколько списаний. У 175533 это
    // 10 и 5 при заказе на 10 — сумма показывается как есть, до суммы заказа не обрезается.
    @Test
    void multiClearing_sumsEveryClearing() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(
                order("175533", "Closed", "FullyPaid", "10", auth("10"), clearing("10"), clearing("5"))));

        Assertions.assertEquals(0, new BigDecimal("15").compareTo(order.capturedAmount()));
        Assertions.assertEquals("SUCCESS", order.status());
    }

    // Пустой ridByMerchant — норма для заказов, заведённых мерчантом у провайдера (Р-69).
    @Test
    void emptyRidByMerchant_staysEmpty() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(
                order("175605", "Closed", "Authorized", "1", null, "AZN", auth("1"))));

        Assertions.assertNull(order.ridByMerchant());
    }

    // На стенде у возврата минус (175195), но итог держится на модуле: если на другом контуре знак
    // окажется плюсом, возвраты не должны стать списаниями.
    @Test
    void refund_isCountedWhicheverSignTheGatewayStoresIt() {
        for (String stored : List.of("-4", "4")) {
            EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(order("175800", "PartPaid",
                    "FullyPaid", "10", single("10"), op("Refund", "Single", null, "Approved", "4", stored))));

            Assertions.assertEquals("PARTIALLY_REFUNDED", order.status(), "clearamt " + stored);
            Assertions.assertEquals(0, new BigDecimal("4").compareTo(order.refundedAmount()), "clearamt " + stored);
            Assertions.assertEquals("REFUND", order.operations().get(1).kind());
        }
    }

    // 175195 со стенда: холд 50, снято 20, списано 30, возвращено тремя частями 5 + 20 + 5. У провайдера
    // это Refused — «полностью возвращён», и выписка обязана сказать то же самое.
    @Test
    void threeRefundsAfterAPartialClearing_isRefunded() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(TxpgRows.refundedAfterPartialClearing()));

        Assertions.assertEquals("REFUNDED", order.status());
        Assertions.assertEquals(0, new BigDecimal("30").compareTo(order.capturedAmount()));
        Assertions.assertEquals(0, new BigDecimal("30").compareTo(order.refundedAmount()));
        Assertions.assertEquals(List.of("AUTHORIZATION", "REVERSAL", "CAPTURE", "REFUND", "REFUND", "REFUND"),
                order.operations().stream().map(EcomOperationResponse::kind).toList());
    }

    // Р-76: частично списанный заказ остаётся Authorized, но деньги по нему уже взяты — это не холд,
    // а частичная оплата (Р-78).
    @Test
    void partialClearingWhileTheOrderIsStillAuthorized_isPartiallyPaid() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(
                order("175196", "Authorized", "Preparing", "50", auth("50"), clearing("30"))));

        Assertions.assertEquals("PARTIALLY_PAID", order.status());
        Assertions.assertEquals(0, new BigDecimal("30").compareTo(order.capturedAmount()));
    }

    // 175164 со стенда: покупка отменена реверсалом до расчётов. Это не возврат — денег мерчант не
    // получал, и провайдер пишет Cancelled. До Р-77 выписка показывала такой заказ «Возвращён».
    @Test
    void fullReversalOfAPurchase_isCanceled_likeTheProviderCancelled() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(order("175164", "Cancelled",
                "Preparing", ".01", single(".01"), op("Purchase", "Single", "Full", "Approved", ".01", "-.01"))));

        Assertions.assertEquals("CANCELED", order.status());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(order.capturedAmount()));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(order.refundedAmount()));
        Assertions.assertEquals("REVERSAL", order.operations().get(1).kind());
    }

    // Частичный реверсал покупки уменьшает списанное, а не превращается в возврат: остаётся
    // частичная оплата.
    @Test
    void partialReversalOfAPurchase_reducesWhatWasCaptured() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(order("175807", "PartPaid",
                "FullyPaid", "10", single("10"), op("Purchase", "Single", "Partial", "Approved", "3", "-3"))));

        Assertions.assertEquals("PARTIALLY_PAID", order.status());
        Assertions.assertEquals(0, new BigDecimal("7").compareTo(order.capturedAmount()));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(order.refundedAmount()));
    }

    // 175204 со стенда: холд снят реверсалом целиком (phase Auth, clearamt 0) — денег не было вовсе.
    @Test
    void reversalOfAHold_isCanceled() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(order("175204", "Cancelled",
                "Preparing", "120", auth("120"), op("Purchase", "Auth", "Full", "Approved", "120", "0"))));

        Assertions.assertEquals("CANCELED", order.status());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(order.capturedAmount()));
    }

    // 175249 со стенда: часть холда снята, остаток списан, часть списанного возвращена. Реверсал
    // холда не трогает ни списанное, ни возвращённое.
    @Test
    void partialReversalOfAHold_leavesTheMoneyAlone() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(order("175249", "Closed", "PartPaid",
                "22", auth("22"), op("Purchase", "Auth", "Partial", "Approved", "5", "0"), clearing("17"),
                op("Refund", "Single", null, "Approved", "7", "-7"))));

        Assertions.assertEquals("PARTIALLY_REFUNDED", order.status());
        Assertions.assertEquals(0, new BigDecimal("17").compareTo(order.capturedAmount()));
        Assertions.assertEquals(0, new BigDecimal("7").compareTo(order.refundedAmount()));
    }

    // Все сочетания статусов из выгрузки стенда 14.09.2026 — по заказу на каждое. Расходится с кодом
    // провайдера одно: DMS Rejected после полного возврата — в истории есть clearamt, статус по деньгам (Р-92).
    @Test
    void everyProviderStatusSeenOnTheStand_mapsAsExpected() {
        Map<String, String> statuses = EcomOrderAssembler.assemble(TxpgRows.providerStatusesSeenOnTheStand()).stream()
                .collect(Collectors.toMap(EcomTransactionResponse::orderId, o -> o.status() + " "
                        + o.capturedAmount().stripTrailingZeros().toPlainString() + "/"
                        + o.refundedAmount().stripTrailingZeros().toPlainString()));

        Assertions.assertEquals(Map.ofEntries(
                Map.entry("176059", "PARTIALLY_PAID 10/0"),     // PartPaid ← Preparing: оплачено 10 из 16
                Map.entry("176001", "SUCCESS 6/0"),             // FullyPaid ← Preparing
                Map.entry("175999", "FAILED 0/0"),              // Expired ← Preparing: в выписку не попадает (Р-71)
                Map.entry("175897", "FAILED 0/0"),              // Rejected ← Preparing
                Map.entry("175700", "CANCELED 0/0"),            // Closed ← Authorized
                Map.entry("175662", "SUCCESS 22/0"),            // Closed ← FullyPaid
                Map.entry("175316", "PARTIALLY_REFUNDED 22/7"), // Closed ← PartPaid
                Map.entry("175247", "PARTIALLY_PAID 20/0"),     // Closed ← PartPaid: снята часть холда
                Map.entry("175203", "REFUNDED 100/100"),        // Refused ← Authorized
                Map.entry("175204", "CANCELED 0/0"),            // Cancelled ← Preparing: холд
                Map.entry("175164", "CANCELED 0/0"),            // Cancelled ← Preparing: покупка
                Map.entry("175378", "FAILED 0/0"),              // Rejected ← Authorized: clearamt пуст (Р-92)
                Map.entry("175246", "REFUNDED 12/12")),         // Rejected ← Refused
                statuses);
    }

    // Требование заказчика (Р-92): FullyPaid — успех, сумма не сверяется. У SMS clearamt бывает пуст,
    // и по деньгам такой заказ выходил отменённым.
    @Test
    void singleMessageFullyPaid_isSuccess_evenWithoutClearAmount() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(order("176100", "FullyPaid",
                "Preparing", "10", op("Purchase", "Single", null, "Approved", "10", null))));

        Assertions.assertEquals("SUCCESS", order.status());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(order.capturedAmount()));
    }

    // Списано меньше суммы заказа, но провайдер пишет FullyPaid — это успех, а не частичная оплата (Р-92).
    @Test
    void singleMessageFullyPaid_isSuccess_evenForLessThanTheOrder() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(order("176101", "FullyPaid",
                "Preparing", "10", single("8"))));

        Assertions.assertEquals("SUCCESS", order.status());
        Assertions.assertEquals(0, new BigDecimal("8").compareTo(order.capturedAmount()));
    }

    // DMS с FullyPaid, в выписке которого нет списания, — тоже успех: статус провайдера важнее истории.
    @Test
    void holdOrderFullyPaid_isSuccess_evenWithoutACapture() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(order("176102", "FullyPaid",
                "Authorized", "20", auth("20"))));

        Assertions.assertEquals("SUCCESS", order.status());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(order.capturedAmount()));
    }

    // DMS, у которого clearamt пуст на всех операциях: по деньгам вышло бы «отменён», провайдер пишет
    // Refused — показывается его статус (Р-92).
    @Test
    void holdOrderWithoutClearAmounts_takesTheProviderStatus() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(order("176103", "Refused",
                "Authorized", "20", auth("20"), op("Purchase", "Clearing", null, "Approved", "20", null),
                op("Refund", "Single", null, "Approved", "20", null))));

        Assertions.assertEquals("REFUNDED", order.status());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(order.capturedAmount()));
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(order.refundedAmount()));
    }

    // SMS читается только по статусу провайдера: Rejected после возврата — отказ, как у провайдера (Р-92).
    @Test
    void singleMessageOrder_takesTheProviderStatus_overTheMoney() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(order("176104", "Rejected",
                "Refused", "12", single("12"), op("Refund", "Single", null, "Approved", "12", "-12"))));

        Assertions.assertEquals("FAILED", order.status());
        Assertions.assertEquals(0, new BigDecimal("12").compareTo(order.refundedAmount()));
    }

    // Контракт (§5.8.8): отрицательный clearAmount — возврат или реверсал, даже без voidkind.
    @Test
    void negativeClearingWithoutVoidKind_isReadAsARefund() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(order("175803", "PartPaid",
                "FullyPaid", "10", single("10"), op("Purchase", "Clearing", null, "Approved", "3", "-3"))));

        Assertions.assertEquals("PARTIALLY_REFUNDED", order.status());
        Assertions.assertEquals(0, new BigDecimal("3").compareTo(order.refundedAmount()));
    }

    @Test
    void declinedAttemptsOnly_isFailed_withTheLastCode() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(order("175804", "Closed", "Preparing",
                "10", op("Purchase", "Single", null, "Declined", "10", "0"),
                op("Purchase", "Single", null, "InsufficientFunds", "10", "0"))));

        Assertions.assertEquals("FAILED", order.status());
        Assertions.assertEquals("InsufficientFunds", order.declineCode());
    }

    @Test
    void declineCode_isEmptyOnceSomethingWasApproved() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(order("175805", "FullyPaid", "Preparing",
                "10", op("Purchase", "Single", null, "Declined", "10", "0"), single("10"))));

        Assertions.assertEquals("SUCCESS", order.status());
        Assertions.assertNull(order.declineCode());
    }

    // Одобренная операция незнакомого вида видна в истории, но не в деньгах — и заказ не выдаётся
    // за неуспешный: платёж с новым кодом провайдера мог и пройти. Статус — FullyPaid провайдера (Р-92).
    @Test
    void approvedButUnknownOperation_isShownButNotCounted_andNotReportedAsFailed() {
        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(order("175806", "FullyPaid", "Preparing",
                "10", op("Purchase", "Installment", null, "Approved", "10", "10"))));

        Assertions.assertEquals("UNKNOWN", order.operations().get(0).kind());
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(order.capturedAmount()));
        Assertions.assertEquals("SUCCESS", order.status());
    }

    @Test
    void operationsAreReturnedInTimeOrder_whateverOrderTheRowsCameIn() {
        List<TxpgStatementRow> rows = new ArrayList<>(
                order("175661", "FullyPaid", "Authorized", "20", auth("20"), clearing("20")));
        Collections.reverse(rows);

        EcomTransactionResponse order = onlyOrder(EcomOrderAssembler.assemble(rows));

        Assertions.assertEquals(List.of("AUTHORIZATION", "CAPTURE"),
                order.operations().stream().map(EcomOperationResponse::kind).toList());
    }

    // Страница отсортирована запросом от новых заказов к старым — склейка этот порядок не ломает.
    @Test
    void ordersKeepTheOrderOfTheQuery() {
        List<EcomTransactionResponse> orders = EcomOrderAssembler.assemble(TxpgRows.testStandExport());

        Assertions.assertEquals(List.of("175700", "175697", "175675"),
                orders.stream().limit(3).map(EcomTransactionResponse::orderId).toList());
    }

    private static EcomTransactionResponse onlyOrder(List<EcomTransactionResponse> orders) {
        Assertions.assertEquals(1, orders.size());
        return orders.get(0);
    }
}
