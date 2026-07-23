package az.millikart.pbl.dto;

import az.millikart.pbl.domain.PaymentLinkStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Request payload for partially updating an existing payment link.
 * Only non-null fields are applied.
 */
public record UpdatePaymentLinkRequest(

        @Positive(message = "amount must be positive")
        @DecimalMin(value = "0.0", inclusive = false, message = "amount must be positive")
        BigDecimal amount,

        String description,

        @Valid
        CustomerDto customer,

        Instant expiresAt,

        @Positive(message = "maxPayments must be greater than 0")
        Integer maxPayments,

        Map<String, Object> metadata,

        PaymentLinkStatus status
) {
}
