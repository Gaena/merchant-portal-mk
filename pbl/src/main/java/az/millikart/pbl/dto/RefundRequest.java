package az.millikart.pbl.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record RefundRequest(
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        @DecimalMin(value = "0.0", inclusive = false, message = "amount must be positive")
        BigDecimal amount,

        String reason
) {
}
