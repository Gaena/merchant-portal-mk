package az.millikart.ecom.service;

import java.math.BigDecimal;

// Статус заказа на вкладке — по деньгам одобренных операций, а не по кодам заказа: словарь
// order_.status провайдер не утверждал, а суммы однозначны. Правило сверху вниз — ecom.md §2.3.
public final class EcomStatusResolver {

    // Значения платёжных ссылок плюс два своих: PARTIALLY_PAID — списано меньше суммы заказа, у
    // провайдера PartPaid (Р-78); CANCELED — одобрено, но ничего не списано (Р-75, Р-77). У ссылок
    // их нет: частичной оплаты портал не делает, Void не умеет (AGENTS.md §10).
    public enum EcomStatus {
        PENDING,
        AUTHORIZED,
        SUCCESS,
        PARTIALLY_PAID,
        FAILED,
        PARTIALLY_REFUNDED,
        REFUNDED,
        CANCELED
    }

    private static final String AUTHORIZED_ORDER = "Authorized";

    private EcomStatusResolver() {
    }

    // capturedAmount — уже за вычетом реверсалов. approvedPayment — была одобрена авторизация или
    // покупка. onlyDeclined — операции были, и ни одна не одобрена; одобренная, но не разобранная
    // операция отказом не считается: платёж с незнакомым кодом не должен читаться как неуспешный.
    public static EcomStatus resolve(BigDecimal capturedAmount,
                                     BigDecimal refundedAmount,
                                     BigDecimal orderAmount,
                                     boolean approvedPayment,
                                     boolean onlyDeclined,
                                     String providerStatus) {
        BigDecimal captured = capturedAmount != null ? capturedAmount : BigDecimal.ZERO;
        BigDecimal refunded = refundedAmount != null ? refundedAmount : BigDecimal.ZERO;

        if (captured.signum() > 0) {
            if (refunded.signum() > 0) {
                // Возврат мерится со списанным, а не с суммой заказа — та же дыра, что P0-8 у ссылок.
                // И он важнее недоплаты: частично оплаченный и частично возвращённый — возврат.
                return refunded.compareTo(captured) >= 0 ? EcomStatus.REFUNDED : EcomStatus.PARTIALLY_REFUNDED;
            }
            // Мультиклиринг бывает и сверх суммы заказа (175533: 15 при заказе на 10) — это полная оплата.
            return orderAmount != null && captured.compareTo(orderAmount) < 0
                    ? EcomStatus.PARTIALLY_PAID
                    : EcomStatus.SUCCESS;
        }
        if (approvedPayment) {
            // Одобрено, но в итоге ничего не списано: холд снят или покупка отменена реверсалом.
            // Пока заказ Authorized, холд ещё могут списать; иначе это отмена (Р-75, Р-77).
            return AUTHORIZED_ORDER.equals(providerStatus) ? EcomStatus.AUTHORIZED : EcomStatus.CANCELED;
        }
        if (refunded.signum() > 0) {
            // Возврат без списания и без холда читать не по чему; строка видна как есть.
            return EcomStatus.REFUNDED;
        }
        return onlyDeclined ? EcomStatus.FAILED : EcomStatus.PENDING;
    }
}
