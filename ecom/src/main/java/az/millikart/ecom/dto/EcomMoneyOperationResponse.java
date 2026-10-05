package az.millikart.ecom.dto;

import java.math.BigDecimal;

// Подтверждённый возврат или списание заказа выписки (Р-125). Идентификаторы — эквайера, из ответа exec-tran
// (контракт §5.7): acquirerReference — tran.match.ridByPmo, без него ответа 200 не бывает; refundId и
// approvalCode бывают пустыми.
public record EcomMoneyOperationResponse(
        String orderId,
        // REFUND или CAPTURE
        String kind,
        BigDecimal amount,
        String refundId,
        String acquirerReference,
        String approvalCode
) {
}
