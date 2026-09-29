package az.millikart.pbl.dto;

import java.math.BigDecimal;
import java.util.UUID;

// Все три идентификатора — эквайера (project_docs/external/TXPG-client-side-integration.md §5.7).
public record RefundResponse(
        UUID transactionId,
        // Статус транзакции после этого возврата: REFUNDED или PARTIALLY_REFUNDED.
        String status,
        BigDecimal amount,
        // tran.match.tranActionId — id возврата в модуле e-commerce; может отсутствовать.
        String refundId,
        // tran.match.ridByPmo — id возврата в ядре процессинга; не пуст: без него возврат не успешен (P1-8b).
        String acquirerReference,
        // tran.approvalCode; может отсутствовать.
        String approvalCode
) {
}
