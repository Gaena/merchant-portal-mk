package az.millikart.ecom.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Строка выписки — заказ со всей его историей, а не операция: у DMS-платежа операций минимум
// две, и построчная выдача задвоила бы платёж, его сумму и пагинацию (Р-74).
public record EcomTransactionResponse(
        String orderId,
        // Reference id мерчанта у провайдера (Р-69).
        String merchantRid,
        String merchantTitle,
        // Reference id платежа от мерчанта. Бывает пустым — ничем не подменять (Р-69).
        String ridByMerchant,
        // Разобранный статус (EcomStatusResolver); сырые коды заказа — рядом.
        String status,
        String providerStatus,
        String providerPrevStatus,
        // Сумма заказа. Списанное — capturedAmount: при частичном списании они разные.
        BigDecimal amount,
        BigDecimal capturedAmount,
        BigDecimal refundedAmount,
        String currency,
        String description,
        Instant createdAt,
        Instant lastOperationAt,
        String cardMask,
        String rrn,
        // Код ответа провайдера по последней операции — только когда одобренных не было.
        String declineCode,
        List<EcomOperationResponse> operations
) {
}
