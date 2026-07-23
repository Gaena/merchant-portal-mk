package az.millikart.pbl.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundResponse(
        UUID transactionId,
        String status,
        BigDecimal amount,
        String refundId
) {
}
