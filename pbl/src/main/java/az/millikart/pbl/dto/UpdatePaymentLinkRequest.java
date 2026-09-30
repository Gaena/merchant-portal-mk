package az.millikart.pbl.dto;

import az.millikart.pbl.domain.PaymentLinkStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

// Применяются только поля, пришедшие не-null.
public record UpdatePaymentLinkRequest(

        @Positive(message = "amount must be positive")
        @DecimalMin(value = "0.0", inclusive = false, message = "amount must be positive")
        BigDecimal amount,

        @Size(max = 255, message = "description must be at most 255 characters")
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
