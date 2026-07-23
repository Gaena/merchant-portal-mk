package az.millikart.pbl.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String status,
        BigDecimal amount,
        String currency,
        String description,
        String merchantOrderId,
        Instant createdAt,
        String customerName,
        String customerEmail,
        String customerPhone
) {
}
