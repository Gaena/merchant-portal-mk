package az.millikart.pbl.dto;

import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.domain.UsageType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Full representation of a payment link, returned by create/get/update endpoints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentLinkResponse(
        UUID id,
        String rid,
        String merchantOrderId,
        Integer terminal,
        BigDecimal amount,
        String currency,
        String description,
        CustomerDto customer,
        PaymentType paymentType,
        UsageType usageType,
        Integer maxPayments,
        Integer currentPaymentsCount,
        PaymentLinkStatus status,
        String link,
        Map<String, Object> metadata,
        Instant createdAt
) {
}
