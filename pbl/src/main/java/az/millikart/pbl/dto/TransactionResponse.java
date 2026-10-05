package az.millikart.pbl.dto;

import az.millikart.common.money.OperationActions;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID paymentLinkId,
        String status,
        BigDecimal amount,
        // Списано при клиринге DMS; null у SMS и у несписанных холдов. Потолок возвратов (P0-8).
        BigDecimal capturedAmount,
        BigDecimal refundedAmount,
        String currency,
        String description,
        String merchantOrderId,
        String paymentType,
        Integer terminalId,
        // Номер платежа у мерчанта, не путать с merchantRid — мерчантом у провайдера (Р-69).
        String ridByMerchant,
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
        // Только записанные события, по возрастанию времени (PaymentLinkService.statusHistoryOf, Р-63).
        List<TransactionEvent> statusHistory,
        // Причина отказа словами эквайера (контракт §5.8.7, Р-24); только у FAILED.
        String failureReason,
        // Кнопки возврата и списания (Р-123); только у одной операции, в списках null.
        OperationActions actions
) {

    // status — состояние ПОСЛЕ события, из словаря TransactionStatus. amount и acquirerReference
    // (tran.match.ridByPmo, контракт §5.7) — только у списания и возврата.
    public record TransactionEvent(
            Instant at,
            // CREATED, CAPTURED, REFUNDED или STATUS — переход, о действии которого записи нет.
            String type,
            String status,
            BigDecimal amount,
            String acquirerReference
    ) {
    }
}
