package az.millikart.pbl.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Модель публичной страницы возврата (redirect.html), намеренно уже TransactionResponse: IP, user agent,
// providerOrderId, карту, RRN и код авторизации плательщику не показывать. Не расширять.
public record PaymentReceiptView(
        String state,
        UUID transactionId,
        BigDecimal amount,
        String currency,
        String merchantOrderId,
        String description,
        String customerName,
        String customerEmail,
        Instant createdAt
) {
}
