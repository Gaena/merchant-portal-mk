package az.millikart.ecom;

import az.millikart.ecom.service.EcomStatusResolver;
import az.millikart.ecom.service.EcomStatusResolver.EcomStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// Статус эквайрингового заказа: по статусу провайдера, а по деньгам — у DMS со списаниями и там, где
// статус ничего не говорит (Р-92). Правило денег — тесты byMoney; код заказа в нём нужен в одном месте —
// отличить холд, который ещё можно списать, от снятого провайдером.
class EcomStatusResolverTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    // Требование заказчика (Р-92): FullyPaid — успех без сверки суммы, у SMS и у DMS, со списаниями или без.
    @Test
    void fullyPaid_isSuccess_whateverTheMoneySays() {
        for (boolean dms : new boolean[] {false, true}) {
            for (boolean moneyMoved : new boolean[] {false, true}) {
                Assertions.assertEquals(EcomStatus.SUCCESS, EcomStatusResolver.resolve(
                        EcomStatus.SUCCESS, EcomStatus.PARTIALLY_PAID, dms, moneyMoved), dms + "/" + moneyMoved);
            }
        }
    }

    // SMS — только статус провайдера, даже когда деньги говорят другое.
    @Test
    void singleMessageOrder_takesTheProviderStatus() {
        Assertions.assertEquals(EcomStatus.FAILED,
                EcomStatusResolver.resolve(EcomStatus.FAILED, EcomStatus.REFUNDED, false, true));
    }

    // 175246 со стенда: DMS Rejected после Refused, списано и возвращено 12. Пока в истории есть clearamt,
    // DMS читается по деньгам: мультиклиринг держит заказ Authorized и после списания (Р-76).
    @Test
    void holdOrderWithClearedMoney_takesTheMoney() {
        Assertions.assertEquals(EcomStatus.REFUNDED,
                EcomStatusResolver.resolve(EcomStatus.FAILED, EcomStatus.REFUNDED, true, true));
        Assertions.assertEquals(EcomStatus.PARTIALLY_PAID,
                EcomStatusResolver.resolve(EcomStatus.AUTHORIZED, EcomStatus.PARTIALLY_PAID, true, true));
    }

    // 175378 со стенда: холд одобрен, не списан, заказ Rejected. clearamt пуст — берётся статус провайдера.
    @Test
    void holdOrderWithoutClearedMoney_takesTheProviderStatus() {
        Assertions.assertEquals(EcomStatus.FAILED,
                EcomStatusResolver.resolve(EcomStatus.FAILED, EcomStatus.CANCELED, true, false));
    }

    // Preparing, пустой и незнакомый статус ничего не говорят — остаются деньги, у SMS тоже.
    @Test
    void silentProviderStatus_fallsBackToTheMoney() {
        for (boolean dms : new boolean[] {false, true}) {
            Assertions.assertEquals(EcomStatus.CANCELED,
                    EcomStatusResolver.resolve(null, EcomStatus.CANCELED, dms, false), "dms " + dms);
        }
    }

    @Test
    void providerDictionary_mapsEveryFinalStatus() {
        Assertions.assertEquals(EcomStatus.SUCCESS, EcomStatusResolver.byProviderStatus("FullyPaid", "Preparing", false));
        Assertions.assertEquals(EcomStatus.PARTIALLY_PAID, EcomStatusResolver.byProviderStatus("PartPaid", "Preparing", false));
        Assertions.assertEquals(EcomStatus.REFUNDED, EcomStatusResolver.byProviderStatus("Refused", "Authorized", true));
        Assertions.assertEquals(EcomStatus.CANCELED, EcomStatusResolver.byProviderStatus("Cancelled", "Preparing", false));
        Assertions.assertEquals(EcomStatus.AUTHORIZED, EcomStatusResolver.byProviderStatus("Authorized", "Preparing", false));
        for (String failed : new String[] {"Rejected", "Declined", "Failed", "Expired"}) {
            Assertions.assertEquals(EcomStatus.FAILED, EcomStatusResolver.byProviderStatus(failed, "Preparing", false), failed);
        }
    }

    // PartPaid по контракту — частично отменён или возвращён. Отличаем по наличию возврата, не по суммам.
    @Test
    void partPaidWithARefund_isPartiallyRefunded() {
        Assertions.assertEquals(EcomStatus.PARTIALLY_REFUNDED,
                EcomStatusResolver.byProviderStatus("PartPaid", "FullyPaid", true));
    }

    // Closed — закрытие, а не результат: на стенде 39 заказов Closed после FullyPaid. Результат — в
    // предыдущем статусе; Closed после Authorized — провайдер сам снял несписанный холд.
    @Test
    void closedOrder_isReadByItsPreviousStatus() {
        Assertions.assertEquals(EcomStatus.SUCCESS, EcomStatusResolver.byProviderStatus("Closed", "FullyPaid", false));
        Assertions.assertEquals(EcomStatus.PARTIALLY_REFUNDED, EcomStatusResolver.byProviderStatus("Closed", "PartPaid", true));
        Assertions.assertEquals(EcomStatus.CANCELED, EcomStatusResolver.byProviderStatus("Closed", "Authorized", false));
        Assertions.assertNull(EcomStatusResolver.byProviderStatus("Closed", "Preparing", false));
        Assertions.assertNull(EcomStatusResolver.byProviderStatus("Closed", "Closed", false));
        Assertions.assertNull(EcomStatusResolver.byProviderStatus("Closed", null, false));
    }

    // Незнакомая форма статуса — не отказ и не успех (Р-20): сверка точная и регистрозависимая.
    @Test
    void unknownProviderStatus_saysNothing() {
        for (String status : new String[] {"Preparing", "fullypaid", " FullyPaid", "Chargeback", ""}) {
            Assertions.assertNull(EcomStatusResolver.byProviderStatus(status, "Preparing", false), status);
        }
        Assertions.assertNull(EcomStatusResolver.byProviderStatus(null, null, false));
    }

    @Test
    void noOperationsAtAll_isPending() {
        Assertions.assertEquals(EcomStatus.PENDING,
                EcomStatusResolver.byMoney(BigDecimal.ZERO, BigDecimal.ZERO, HUNDRED, false, false, "Preparing"));
    }

    // Операции были, ни одна не одобрена — это отказ, и мерчанту он нужен: именно с ним приходят
    // разбираться, когда «клиент говорит, что платил».
    @Test
    void operationsButNoneApproved_isFailed() {
        Assertions.assertEquals(EcomStatus.FAILED,
                EcomStatusResolver.byMoney(BigDecimal.ZERO, BigDecimal.ZERO, HUNDRED, false, true, "Closed"));
    }

    @Test
    void approvedAuthorizationWithoutCapture_whileTheOrderIsAuthorized_isAuthorized() {
        Assertions.assertEquals(EcomStatus.AUTHORIZED,
                EcomStatusResolver.byMoney(BigDecimal.ZERO, BigDecimal.ZERO, HUNDRED, true, false, "Authorized"));
    }

    // Closed после Authorized: провайдер сам снял холд, по которому не списали (ответ провайдера
    // 14.09.2026). Ни «успешно», ни «отказ» — банк карту одобрил, денег не взяли.
    @Test
    void approvedAuthorizationWithoutCapture_afterTheProviderClosedTheOrder_isCanceled() {
        Assertions.assertEquals(EcomStatus.CANCELED,
                EcomStatusResolver.byMoney(BigDecimal.ZERO, BigDecimal.ZERO, HUNDRED, true, false, "Closed"));
    }

    // Реверсал холда может нести отрицательную сумму — возвращать при этом нечего.
    @Test
    void releasedHoldCarryingAReturnedAmount_isCanceledNotRefunded() {
        Assertions.assertEquals(EcomStatus.CANCELED,
                EcomStatusResolver.byMoney(BigDecimal.ZERO, HUNDRED, HUNDRED, true, false, "Cancelled"));
    }

    // Покупка, отменённая реверсалом: списанное после реверсала — ноль, одобренная покупка была.
    // У провайдера это Cancelled, не Refused (Р-77).
    @Test
    void purchaseReversedInFull_isCanceled() {
        Assertions.assertEquals(EcomStatus.CANCELED,
                EcomStatusResolver.byMoney(BigDecimal.ZERO, BigDecimal.ZERO, HUNDRED, true, false, "Cancelled"));
    }

    @Test
    void capturedMoney_isSuccess() {
        Assertions.assertEquals(EcomStatus.SUCCESS,
                EcomStatusResolver.byMoney(HUNDRED, BigDecimal.ZERO, HUNDRED, true, false, "FullyPaid"));
    }

    // Р-78: списано меньше суммы заказа — у провайдера PartPaid (оплата 10 при заказе на 16, снятая
    // часть холда). Раньше такой заказ выходил просто «успешно», и недоплата пряталась.
    @Test
    void capturedLessThanTheOrder_isPartiallyPaid() {
        Assertions.assertEquals(EcomStatus.PARTIALLY_PAID,
                EcomStatusResolver.byMoney(BigDecimal.TEN, BigDecimal.ZERO, new BigDecimal("16"), true, false, "PartPaid"));
    }

    // Мультиклиринг бывает сверх суммы заказа (175533: 15 при заказе на 10) — это не недоплата.
    @Test
    void capturedBeyondTheOrder_isSuccess() {
        Assertions.assertEquals(EcomStatus.SUCCESS,
                EcomStatusResolver.byMoney(new BigDecimal("15"), BigDecimal.ZERO, BigDecimal.TEN, true, false, "Closed"));
    }

    // Возврат важнее недоплаты: 175202 — списано 100 из 120 и возвращено 10.
    @Test
    void partlyPaidAndPartlyRefunded_isPartiallyRefunded() {
        Assertions.assertEquals(EcomStatus.PARTIALLY_REFUNDED,
                EcomStatusResolver.byMoney(new BigDecimal("100"), BigDecimal.TEN, new BigDecimal("120"), true, false, "Closed"));
    }

    @Test
    void unknownOrderAmount_isReadAsFullyPaid() {
        Assertions.assertEquals(EcomStatus.SUCCESS,
                EcomStatusResolver.byMoney(BigDecimal.TEN, BigDecimal.ZERO, null, true, false, "FullyPaid"));
    }

    @Test
    void partOfTheCapturedMoneyReturned_isPartiallyRefunded() {
        Assertions.assertEquals(EcomStatus.PARTIALLY_REFUNDED,
                EcomStatusResolver.byMoney(HUNDRED, new BigDecimal("40.00"), HUNDRED, true, false, "PartPaid"));
    }

    @Test
    void allOfTheCapturedMoneyReturned_isRefunded() {
        Assertions.assertEquals(EcomStatus.REFUNDED,
                EcomStatusResolver.byMoney(HUNDRED, new BigDecimal("100.00"), HUNDRED, true, false, "Refused"));
    }

    // Списали 500 из авторизованных 1500 и вернули эти же 500: возврат полный, потому что мерится
    // он со списанным, а не с суммой заказа. Та же дыра, что закрывал P0-8 у платёжных ссылок.
    @Test
    void refundIsMeasuredAgainstWhatWasCaptured_notAgainstTheOrder() {
        Assertions.assertEquals(EcomStatus.REFUNDED,
                EcomStatusResolver.byMoney(new BigDecimal("500.00"), new BigDecimal("500.00"), new BigDecimal("1500.00"), true, false, "Refused"));
    }

    // Возврат больше списанного бывает от расхождения словаря операций: строка всё равно должна
    // читаться как возвращённая, а не молча стать частичной.
    @Test
    void refundLargerThanTheCapture_stillReadsAsRefunded() {
        Assertions.assertEquals(EcomStatus.REFUNDED,
                EcomStatusResolver.byMoney(HUNDRED, new BigDecimal("120.00"), HUNDRED, true, false, "Refused"));
    }

    @Test
    void nullAmounts_areReadAsZero() {
        Assertions.assertEquals(EcomStatus.PENDING,
                EcomStatusResolver.byMoney(null, null, null, false, false, null));
    }
}
