package az.millikart.pbl.dto;

import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.domain.UsageType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentLinkResponse(
        UUID id,
        String rid,
        String merchantOrderId,
        Integer terminal,
        BigDecimal amount,
        String currency,
        String description,
        CustomerDto customer,
        PaymentType paymentType,
        UsageType usageType,
        Integer maxPayments,
        // Сколько раз ссылкой воспользовались (P2-16, Р-49): состоявшиеся платежи — SUCCESS,
        // REFUNDED и PARTIALLY_REFUNDED вместе (TransactionStatus.PAID_STATUSES). Возврат это число
        // не снижает и слот не освобождает. Имя старше правила и остаётся: оно уже в API.
        Integer currentPaymentsCount,
        // Сколько платежей ссылки вернули, полностью или частично (P2-16, Р-50): REFUNDED +
        // PARTIALLY_REFUNDED, никогда не больше currentPaymentsCount. Только в одиночном ответе;
        // в PaymentLinkSummaryResponse не добавлять — список строится без похода в транзакции, и
        // счётчик на строку вернёт N+1, снятый в P2-15.
        Integer refundedPaymentsCount,
        PaymentLinkStatus status,
        String link,
        Map<String, Object> metadata,
        // Момент, когда ссылка перестаёт быть оплачиваемой. Есть у всех ссылок с 18.08.2026 (P1-9);
        // null только на старых строках, которые не истекают никогда. Раньше поля здесь не было при
        // наличии его в PaymentLinkSummaryResponse — портал рисовал отсчёт «сейчас + 24 ч» над
        // ссылкой, которая на деле не кончалась.
        Instant expiresAt,
        // Время последней оплаты (P2-15) или null, если её не было. Именно lastPaidAt, а не paidAt:
        // у многоразовой ссылки платежей много, и это самый свежий из них (Р-46). Возвращённый
        // платёж считается тоже — дата оплаты с возвратом не исчезает.
        Instant lastPaidAt,
        Instant createdAt
) {
}
