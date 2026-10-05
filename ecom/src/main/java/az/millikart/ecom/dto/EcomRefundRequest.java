package az.millikart.ecom.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// Возврат заказа выписки (Р-125): сумма до actions.refund.maxAmount и необязательная причина — в журнал (Р-126).
public record EcomRefundRequest(
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        BigDecimal amount,

        @Size(max = 255, message = "reason must be at most 255 characters")
        String reason
) {
}
