package az.millikart.pbl.dto;

import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.domain.UsageType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentLinkSummaryResponse(
        UUID id,
        PaymentLinkStatus status,
        BigDecimal amount,
        String currency,
        String description,
        String customerName,
        String customerEmail,
        String customerPhone,
        PaymentType paymentType,
        UsageType usageType,
        Integer maxPayments,
        Instant expiresAt,
        // Время последней оплаты (P2-15) или null; то же значение и то же имя, что в
        // PaymentLinkResponse. На страницу заполняется одним группирующим запросом на всю страницу,
        // а не выборкой на строку (PaymentLinkService.list).
        Instant lastPaidAt,
        Instant createdAt
) {
}
