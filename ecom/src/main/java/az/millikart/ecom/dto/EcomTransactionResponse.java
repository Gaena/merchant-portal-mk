package az.millikart.ecom.dto;

import az.millikart.common.money.OperationActions;
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
        List<EcomOperationResponse> operations,
        // Кнопки возврата и списания (Р-124) — только в карточке заказа, в строках выписки null.
        OperationActions actions,
        // Заказ заведён порталом: проводит его pbl по этой операции, своих кнопок у заказа нет (Р-124).
        String portalTransactionId
) {

    public EcomTransactionResponse withActions(OperationActions actions, String portalTransactionId) {
        return new EcomTransactionResponse(orderId, merchantRid, merchantTitle, ridByMerchant, status, providerStatus,
                providerPrevStatus, amount, capturedAmount, refundedAmount, currency, description, createdAt,
                lastOperationAt, cardMask, rrn, declineCode, operations, actions, portalTransactionId);
    }
}
