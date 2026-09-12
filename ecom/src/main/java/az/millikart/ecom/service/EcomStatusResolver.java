package az.millikart.ecom.service;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Во что превращается эквайринговый заказ на нашей вкладке.
 *
 * Решение принимается **по деньгам, а не по кодам статуса**, и это сделано намеренно. Полного
 * словаря `order_.status`, `prevstatus`, `tran.trantype` и `pmoresultcode` провайдер пока не
 * утверждал: на выгрузках со стенда видны `Closed`, `FullyPaid`, `Authorized`, `Preparing` —
 * и это заведомо не весь список. Маппить незнакомые коды в наши шесть значений значило бы гадать,
 * а суммы одобренных операций однозначны в любой версии их словаря.
 *
 * Правило, сверху вниз:
 *
 *   возвращено столько же, сколько списано  → REFUNDED
 *   возвращено меньше, но больше нуля       → PARTIALLY_REFUNDED
 *   что-то списано                          → SUCCESS
 *   одобренная авторизация без списания     → AUTHORIZED
 *   операции были, ни одна не одобрена      → FAILED
 *   операций не было вовсе                  → PENDING
 *
 * Когда словарь придёт, это место станет первым кандидатом на уточнение: chargeback,
 * представление и `void` против `refund` здесь сейчас не различаются никак — их просто нечем
 * различать. Сырые коды заказа при этом уезжают на экран как есть, рядом с разобранным статусом,
 * чтобы расхождение было видно, а не пряталось.
 */
public final class EcomStatusResolver {

    private static final Logger log = LoggerFactory.getLogger(EcomStatusResolver.class);

    /** Ровно те же шесть значений, что у платёжных ссылок: фронтенд разбирает их одним разбором. */
    public enum EcomStatus {
        PENDING,
        AUTHORIZED,
        SUCCESS,
        FAILED,
        PARTIALLY_REFUNDED,
        REFUNDED
    }

    private EcomStatusResolver() {
    }

    public static EcomStatus resolve(BigDecimal capturedAmount,
                                     BigDecimal refundedAmount,
                                     boolean hasApprovedAuthorization,
                                     int operationCount) {
        BigDecimal captured = capturedAmount != null ? capturedAmount : BigDecimal.ZERO;
        BigDecimal refunded = refundedAmount != null ? refundedAmount : BigDecimal.ZERO;

        if (refunded.signum() > 0) {
            if (captured.signum() <= 0) {
                // Возврат без списания читать не по чему: денег, которые можно было бы вернуть,
                // по нашим же данным не уходило. Строка видна как есть, но подпись у неё честная.
                log.warn("Order carries refunds of {} with nothing captured; reporting it as REFUNDED, "
                        + "the operation dictionary needs checking", refunded);
                return EcomStatus.REFUNDED;
            }
            return refunded.compareTo(captured) >= 0 ? EcomStatus.REFUNDED : EcomStatus.PARTIALLY_REFUNDED;
        }
        if (captured.signum() > 0) {
            return EcomStatus.SUCCESS;
        }
        if (hasApprovedAuthorization) {
            return EcomStatus.AUTHORIZED;
        }
        return operationCount > 0 ? EcomStatus.FAILED : EcomStatus.PENDING;
    }
}
