package az.millikart.pbl.dto;

import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.domain.UsageType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Compact representation of a payment link used in list responses.
 */
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
        Instant createdAt
) {
}
