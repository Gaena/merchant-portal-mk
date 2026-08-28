package az.millikart.pbl.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID paymentLinkId,
        String status,
        BigDecimal amount,
        // Сколько эквайер реально склирил при списании DMS; null для SMS-платежей и для холдов,
        // которые не списывали. После частичного списания меньше amount и служит потолком, по
        // которому меряется каждый возврат этой транзакции.
        BigDecimal capturedAmount,
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
        String providerOrderId,
        // Причина отказа словами эквайера: custAttrs DeclineDescription, иначе
        // PmoDeclineDescription, иначе PmoResultCode (контракт §5.8.7). Заполнен только у FAILED,
        // чей финальный опрос статуса принёс причину; иначе null.
        String failureReason
) {
}
