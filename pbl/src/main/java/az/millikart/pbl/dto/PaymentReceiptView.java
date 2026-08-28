package az.millikart.pbl.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Модель страницы возврата плательщика (templates/redirect.html), намеренно уже, чем
// TransactionResponse: плательщику нельзя показывать clientIp, userAgent, providerOrderId,
// маскированную карту, RRN и код авторизации. Не расширять — новые поля добавлять в
// TransactionResponse, который отдаётся только аутентифицированным пользователям мерчанта.
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
