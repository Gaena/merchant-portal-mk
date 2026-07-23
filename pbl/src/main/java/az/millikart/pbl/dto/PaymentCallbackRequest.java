package az.millikart.pbl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Asynchronous payment-result callback sent by the acquiring provider (TXPG)
 * after the customer completes (or fails) a payment at the gateway.
 *
 * @param rid          provider reference id of the transaction ({@code external_rid})
 * @param result       outcome of the payment attempt
 * @param amount       amount actually processed by the provider (optional)
 * @param providerData raw provider payload for auditing (optional)
 */
public record PaymentCallbackRequest(

        @NotBlank(message = "rid is required")
        String rid,

        @NotNull(message = "result is required")
        CallbackResult result,

        BigDecimal amount,

        Map<String, Object> providerData
) {

    /**
     * Outcome reported by the provider for a payment attempt.
     */
    public enum CallbackResult {
        SUCCESS,
        FAILED
    }
}
