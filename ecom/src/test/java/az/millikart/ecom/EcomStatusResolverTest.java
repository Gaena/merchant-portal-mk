package az.millikart.ecom;

import az.millikart.ecom.service.EcomStatusResolver;
import az.millikart.ecom.service.EcomStatusResolver.EcomStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Статус эквайрингового заказа выводится из денег, а не из кодов провайдера: полного словаря
 * `order_.status` / `tran.trantype` / `pmoresultcode` у нас нет, а суммы одобренных операций
 * однозначны в любой его версии.
 */
class EcomStatusResolverTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    @Test
    void noOperationsAtAll_isPending() {
        Assertions.assertEquals(EcomStatus.PENDING,
                EcomStatusResolver.resolve(BigDecimal.ZERO, BigDecimal.ZERO, false, 0));
    }

    // Операции были, ни одна не одобрена — это отказ, и мерчанту он нужен: именно с ним приходят
    // разбираться, когда «клиент говорит, что платил».
    @Test
    void operationsButNoneApproved_isFailed() {
        Assertions.assertEquals(EcomStatus.FAILED,
                EcomStatusResolver.resolve(BigDecimal.ZERO, BigDecimal.ZERO, false, 3));
    }

    @Test
    void approvedAuthorizationWithoutCapture_isAuthorized() {
        Assertions.assertEquals(EcomStatus.AUTHORIZED,
                EcomStatusResolver.resolve(BigDecimal.ZERO, BigDecimal.ZERO, true, 1));
    }

    @Test
    void capturedMoney_isSuccess() {
        Assertions.assertEquals(EcomStatus.SUCCESS,
                EcomStatusResolver.resolve(HUNDRED, BigDecimal.ZERO, true, 2));
    }

    @Test
    void partOfTheCapturedMoneyReturned_isPartiallyRefunded() {
        Assertions.assertEquals(EcomStatus.PARTIALLY_REFUNDED,
                EcomStatusResolver.resolve(HUNDRED, new BigDecimal("40.00"), true, 3));
    }

    @Test
    void allOfTheCapturedMoneyReturned_isRefunded() {
        Assertions.assertEquals(EcomStatus.REFUNDED,
                EcomStatusResolver.resolve(HUNDRED, new BigDecimal("100.00"), true, 3));
    }

    // Списали 500 из авторизованных 1500 и вернули эти же 500: возврат полный, потому что мерятся
    // он со списанным, а не с суммой заказа. Та же дыра, что закрывал P0-8 у платёжных ссылок.
    @Test
    void refundIsMeasuredAgainstWhatWasCaptured_notAgainstTheOrder() {
        Assertions.assertEquals(EcomStatus.REFUNDED,
                EcomStatusResolver.resolve(new BigDecimal("500.00"), new BigDecimal("500.00"), true, 3));
    }

    // Возврат больше списанного бывает от расхождения словаря операций: строка всё равно должна
    // читаться как возвращённая, а не молча стать частичной.
    @Test
    void refundLargerThanTheCapture_stillReadsAsRefunded() {
        Assertions.assertEquals(EcomStatus.REFUNDED,
                EcomStatusResolver.resolve(HUNDRED, new BigDecimal("120.00"), true, 4));
    }

    @Test
    void nullAmounts_areReadAsZero() {
        Assertions.assertEquals(EcomStatus.PENDING,
                EcomStatusResolver.resolve(null, null, false, 0));
    }
}
