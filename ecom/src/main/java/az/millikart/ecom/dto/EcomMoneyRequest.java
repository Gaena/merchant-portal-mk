package az.millikart.ecom.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

// Сумма возврата или списания заказа выписки (Р-125); потолок — maxAmount в actions карточки.
public record EcomMoneyRequest(
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        BigDecimal amount
) {
}
