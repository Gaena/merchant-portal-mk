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
        // Платежи из PAID_STATUSES (P2-16, Р-49): возврат число не снижает. Имя — часть API, не менять.
        Integer currentPaymentsCount,
        // REFUNDED + PARTIALLY_REFUNDED (Р-50), не больше currentPaymentsCount. В PaymentLinkSummaryResponse
        // не добавлять — счётчик на строку списка это N+1 (P2-15).
        Integer refundedPaymentsCount,
        PaymentLinkStatus status,
        String link,
        Map<String, Object> metadata,
        // null только у строк до P1-9 — такие ссылки не истекают никогда.
        Instant expiresAt,
        // Самая свежая оплата из PAID_STATUSES (P2-15, Р-46), с возвратом не исчезает; null — оплат не было.
        Instant lastPaidAt,
        Instant createdAt
) {
}
