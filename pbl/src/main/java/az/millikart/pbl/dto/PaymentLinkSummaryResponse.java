package az.millikart.pbl.dto;

import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.domain.UsageType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentLinkSummaryResponse(
        UUID id,
        PaymentLinkStatus status,
        // Только номер: подпись терминала фронтенд берёт из /terminals/options.
        Integer terminal,
        BigDecimal amount,
        String currency,
        String description,
        String customerName,
        String customerEmail,
        String customerPhone,
        PaymentType paymentType,
        UsageType usageType,
        Integer maxPayments,
        Instant expiresAt,
        // Как в PaymentLinkResponse; на страницу — один запрос, а не запрос на строку (P2-15).
        Instant lastPaidAt,
        Instant createdAt
) {
}
