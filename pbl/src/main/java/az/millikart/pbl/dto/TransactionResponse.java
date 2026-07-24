package az.millikart.pbl.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID paymentLinkId,
        String status,
        BigDecimal amount,
        BigDecimal refundedAmount,
        String currency,
        String description,
        String merchantOrderId,
        String paymentType,
        Integer terminalId,
        String merchantRid,
        String cardNumberMasked,
        String rrn,
        String approvalCode,
        Instant createdAt,
        String customerName,
        String customerEmail,
        String customerPhone,
        String clientIp,
        String userAgent,
        String providerOrderId
) {
}
