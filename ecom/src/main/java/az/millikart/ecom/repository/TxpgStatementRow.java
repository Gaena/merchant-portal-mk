package az.millikart.ecom.repository;

import java.math.BigDecimal;
import java.time.Instant;

// Строка выборки выписки: заказ и одна его операция, как их отдаёт схема шлюза. Колонки — ровно
// те, что есть в запросе провайдера; в заказы строки склеивает EcomOrderAssembler (Р-74).
public record TxpgStatementRow(
        String orderId,
        String ridByMerchant,
        String orderStatus,
        String orderPrevStatus,
        String description,
        BigDecimal orderAmount,
        String orderCurrency,
        Instant orderCreatedAt,
        String merchantRid,
        String merchantTitle,
        String cardMask,
        // tran.ridbyacq: 18 цифр, только строкой — в double последние знаки выдумываются.
        String tranId,
        String rrn,
        Instant tranAt,
        String resultCode,
        BigDecimal tranAmount,
        BigDecimal clearAmount,
        String tranCurrency,
        String tranType,
        String phase,
        String voidKind,
        String authKind
) {
}
