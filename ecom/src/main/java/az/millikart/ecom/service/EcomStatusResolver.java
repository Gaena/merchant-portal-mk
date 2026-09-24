package az.millikart.ecom.service;

import java.math.BigDecimal;

// Статус заказа на вкладке — по статусу заказа у провайдера, а по деньгам одобренных операций — только
// у DMS со списаниями и там, где статус провайдера ничего не говорит (Р-92). Правило — ecom.md §2.3.
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
    private static final String CLOSED_ORDER = "Closed";

    private EcomStatusResolver() {
    }

    // FullyPaid — всегда успех, сумма не сверяется. SMS — по статусу провайдера. DMS — по деньгам, пока в
    // истории есть clearamt: мультиклиринг держит заказ Authorized и после списания (Р-76). Статус, который
    // ничего не говорит (Preparing, незнакомый), — по деньгам (Р-92).
    public static EcomStatus resolve(EcomStatus byProvider, EcomStatus byMoney, boolean dms, boolean moneyMoved) {
        if (byProvider == null) {
            return byMoney;
        }
        if (byProvider == EcomStatus.SUCCESS) {
            return byProvider;
        }
        return dms && moneyMoved ? byMoney : byProvider;
    }

    // Словарь — контракт §5.8.8 и выгрузка стенда 14.09.2026; Declined и Failed — как в pbl
    // (ProviderOrderStatus). Сверка точная и регистрозависимая: незнакомая форма — null, а не отказ (Р-20).
    // Closed — не результат, а закрытие заказа: результат несёт предыдущий статус.
    public static EcomStatus byProviderStatus(String status, String prevStatus, boolean refundApproved) {
        if (CLOSED_ORDER.equals(status)) {
            // Closed после Authorized — провайдер сам снял холд, который не списали.
            if (AUTHORIZED_ORDER.equals(prevStatus)) {
                return EcomStatus.CANCELED;
            }
            return CLOSED_ORDER.equals(prevStatus) ? null : byProviderStatus(prevStatus, null, refundApproved);
        }
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "FullyPaid" -> EcomStatus.SUCCESS;
            // PartPaid по контракту — частично отменён или возвращён, на стенде — и оплачен меньше суммы.
            // Отличаем по наличию возврата, не по суммам: у SMS суммы не сверяются.
            case "PartPaid" -> refundApproved ? EcomStatus.PARTIALLY_REFUNDED : EcomStatus.PARTIALLY_PAID;
            case "Refused" -> EcomStatus.REFUNDED;
            case "Cancelled" -> EcomStatus.CANCELED;
            case "Rejected", "Declined", "Failed", "Expired" -> EcomStatus.FAILED;
            case AUTHORIZED_ORDER -> EcomStatus.AUTHORIZED;
            default -> null;
        };
    }

    // capturedAmount — уже за вычетом реверсалов. approvedPayment — была одобрена авторизация или
    // покупка. onlyDeclined — операции были, и ни одна не одобрена; одобренная, но не разобранная
    // операция отказом не считается: платёж с незнакомым кодом не должен читаться как неуспешный.
    public static EcomStatus byMoney(BigDecimal capturedAmount,
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
