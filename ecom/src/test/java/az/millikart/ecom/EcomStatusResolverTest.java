package az.millikart.ecom;

import az.millikart.ecom.service.EcomStatusResolver;
import az.millikart.ecom.service.EcomStatusResolver.EcomStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// Статус эквайрингового заказа выводится из денег, а не из кодов провайдера: словарь order_.status
// не утверждён, а суммы одобренных операций однозначны. Код заказа нужен в одном месте — отличить
// холд, который ещё можно списать, от снятого провайдером.
class EcomStatusResolverTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    @Test
    void noOperationsAtAll_isPending() {
        Assertions.assertEquals(EcomStatus.PENDING,
                EcomStatusResolver.resolve(BigDecimal.ZERO, BigDecimal.ZERO, HUNDRED, false, false, "Preparing"));
    }

    // Операции были, ни одна не одобрена — это отказ, и мерчанту он нужен: именно с ним приходят
    // разбираться, когда «клиент говорит, что платил».
    @Test
    void operationsButNoneApproved_isFailed() {
        Assertions.assertEquals(EcomStatus.FAILED,
                EcomStatusResolver.resolve(BigDecimal.ZERO, BigDecimal.ZERO, HUNDRED, false, true, "Closed"));
    }

    @Test
    void approvedAuthorizationWithoutCapture_whileTheOrderIsAuthorized_isAuthorized() {
        Assertions.assertEquals(EcomStatus.AUTHORIZED,
                EcomStatusResolver.resolve(BigDecimal.ZERO, BigDecimal.ZERO, HUNDRED, true, false, "Authorized"));
    }

    // Closed после Authorized: провайдер сам снял холд, по которому не списали (ответ провайдера
    // 14.09.2026). Ни «успешно», ни «отказ» — банк карту одобрил, денег не взяли.
    @Test
    void approvedAuthorizationWithoutCapture_afterTheProviderClosedTheOrder_isCanceled() {
        Assertions.assertEquals(EcomStatus.CANCELED,
                EcomStatusResolver.resolve(BigDecimal.ZERO, BigDecimal.ZERO, HUNDRED, true, false, "Closed"));
    }

    // Реверсал холда может нести отрицательную сумму — возвращать при этом нечего.
    @Test
    void releasedHoldCarryingAReturnedAmount_isCanceledNotRefunded() {
        Assertions.assertEquals(EcomStatus.CANCELED,
                EcomStatusResolver.resolve(BigDecimal.ZERO, HUNDRED, HUNDRED, true, false, "Cancelled"));
    }

    // Покупка, отменённая реверсалом: списанное после реверсала — ноль, одобренная покупка была.
    // У провайдера это Cancelled, не Refused (Р-77).
    @Test
    void purchaseReversedInFull_isCanceled() {
        Assertions.assertEquals(EcomStatus.CANCELED,
                EcomStatusResolver.resolve(BigDecimal.ZERO, BigDecimal.ZERO, HUNDRED, true, false, "Cancelled"));
    }

    @Test
    void capturedMoney_isSuccess() {
        Assertions.assertEquals(EcomStatus.SUCCESS,
                EcomStatusResolver.resolve(HUNDRED, BigDecimal.ZERO, HUNDRED, true, false, "FullyPaid"));
    }

    // Р-78: списано меньше суммы заказа — у провайдера PartPaid (оплата 10 при заказе на 16, снятая
    // часть холда). Раньше такой заказ выходил просто «успешно», и недоплата пряталась.
    @Test
    void capturedLessThanTheOrder_isPartiallyPaid() {
        Assertions.assertEquals(EcomStatus.PARTIALLY_PAID,
                EcomStatusResolver.resolve(BigDecimal.TEN, BigDecimal.ZERO, new BigDecimal("16"), true, false, "PartPaid"));
    }

    // Мультиклиринг бывает сверх суммы заказа (175533: 15 при заказе на 10) — это не недоплата.
    @Test
    void capturedBeyondTheOrder_isSuccess() {
        Assertions.assertEquals(EcomStatus.SUCCESS,
                EcomStatusResolver.resolve(new BigDecimal("15"), BigDecimal.ZERO, BigDecimal.TEN, true, false, "Closed"));
    }

    // Возврат важнее недоплаты: 175202 — списано 100 из 120 и возвращено 10.
    @Test
    void partlyPaidAndPartlyRefunded_isPartiallyRefunded() {
        Assertions.assertEquals(EcomStatus.PARTIALLY_REFUNDED,
                EcomStatusResolver.resolve(new BigDecimal("100"), BigDecimal.TEN, new BigDecimal("120"), true, false, "Closed"));
    }

    @Test
    void unknownOrderAmount_isReadAsFullyPaid() {
        Assertions.assertEquals(EcomStatus.SUCCESS,
                EcomStatusResolver.resolve(BigDecimal.TEN, BigDecimal.ZERO, null, true, false, "FullyPaid"));
    }

    @Test
    void partOfTheCapturedMoneyReturned_isPartiallyRefunded() {
        Assertions.assertEquals(EcomStatus.PARTIALLY_REFUNDED,
                EcomStatusResolver.resolve(HUNDRED, new BigDecimal("40.00"), HUNDRED, true, false, "PartPaid"));
    }

    @Test
    void allOfTheCapturedMoneyReturned_isRefunded() {
        Assertions.assertEquals(EcomStatus.REFUNDED,
                EcomStatusResolver.resolve(HUNDRED, new BigDecimal("100.00"), HUNDRED, true, false, "Refused"));
    }

    // Списали 500 из авторизованных 1500 и вернули эти же 500: возврат полный, потому что мерится
    // он со списанным, а не с суммой заказа. Та же дыра, что закрывал P0-8 у платёжных ссылок.
    @Test
    void refundIsMeasuredAgainstWhatWasCaptured_notAgainstTheOrder() {
        Assertions.assertEquals(EcomStatus.REFUNDED,
                EcomStatusResolver.resolve(new BigDecimal("500.00"), new BigDecimal("500.00"), new BigDecimal("1500.00"), true, false, "Refused"));
    }

    // Возврат больше списанного бывает от расхождения словаря операций: строка всё равно должна
    // читаться как возвращённая, а не молча стать частичной.
    @Test
    void refundLargerThanTheCapture_stillReadsAsRefunded() {
        Assertions.assertEquals(EcomStatus.REFUNDED,
                EcomStatusResolver.resolve(HUNDRED, new BigDecimal("120.00"), HUNDRED, true, false, "Refused"));
    }

    @Test
    void nullAmounts_areReadAsZero() {
        Assertions.assertEquals(EcomStatus.PENDING,
                EcomStatusResolver.resolve(null, null, null, false, false, null));
    }
}
