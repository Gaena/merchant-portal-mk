package az.millikart.ecom.dto;

import java.math.BigDecimal;
import java.time.Instant;

// Операция заказа — строка его истории. kind — наш разбор, рядом сырые коды провайдера: их словарь
// не утверждён, и расхождение разбора с кодами должно быть видно, а не спрятано.
public record EcomOperationResponse(
        // tran.ridbyacq: 18 цифр, только строкой — в double последние знаки выдумываются.
        String tranId,
        Instant at,
        // AUTHORIZATION, CAPTURE, PURCHASE, REVERSAL, REFUND или UNKNOWN (EcomOperationKind).
        String kind,
        String type,
        String phase,
        String voidKind,
        String authKind,
        String resultCode,
        BigDecimal amount,
        // Сколько операция списала: у авторизации и реверсала холда 0, у возврата и реверсала покупки — с минусом.
        BigDecimal clearAmount,
        String currency,
        String rrn
) {
}
