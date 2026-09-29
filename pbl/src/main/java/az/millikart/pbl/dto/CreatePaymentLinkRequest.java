package az.millikart.pbl.dto;

import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.domain.UsageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record CreatePaymentLinkRequest(

        String merchantOrderId,

        @NotNull(message = "terminal is required")
        Integer terminal,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        @DecimalMin(value = "0.0", inclusive = false, message = "amount must be positive")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
        String currency,

        String description,

        @Valid
        CustomerDto customer,

        @NotNull(message = "paymentType is required")
        PaymentType paymentType,

        @NotNull(message = "usageType is required")
        UsageType usageType,

        @Positive(message = "maxPayments must be greater than 0")
        Integer maxPayments,

        // Без него — pbl.link.default-ttl. @Future не ставить: обе границы проверяет сервис, и отказ
        // называет превышенный потолок pbl.link.max-ttl (P1-9).
        Instant expiresAt,

        Map<String, Object> metadata
) {

    @AssertTrue(message = "maxPayments is required and must be greater than 0 when usageType is MULTIPLE")
    public boolean isMaxPaymentsValid() {
        if (usageType == UsageType.MULTIPLE) {
            return maxPayments != null && maxPayments > 0;
        }
        return true;
    }
}
