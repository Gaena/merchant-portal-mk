package az.millikart.pbl.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record RefundRequest(
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        @DecimalMin(value = "0.0", inclusive = false, message = "amount must be positive")
        BigDecimal amount,

        // Необязательная причина — в запись журнала REFUND, для разбора спора (Р-126). Эквайеру не уходит: в
        // exec-tran такого поля нет.
        @Size(max = 255, message = "reason must be at most 255 characters")
        String reason
) {
}
