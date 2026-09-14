package az.millikart.pbl.dto;

import java.math.BigDecimal;
import java.util.UUID;

// Ответ на подтверждённый возврат. Все три идентификатора — собственные идентификаторы эквайера
// (project_docs/TXPG-client-side-integration.md §5.7), локально не генерируется ни один.
public record RefundResponse(
        UUID transactionId,
        // Статус транзакции после этого возврата: REFUNDED или PARTIALLY_REFUNDED.
        String status,
        BigDecimal amount,
        // tran.match.tranActionId — id возврата в модуле e-commerce; может отсутствовать.
        String refundId,
        // tran.match.ridByPmo — id возврата в ядре процессинга, та ссылка, что весома в споре.
        // В успешном ответе не пуст: возврат без него успешным вообще не рапортуется (P1-8b).
        String acquirerReference,
        // tran.approvalCode; может отсутствовать.
        String approvalCode
) {
}
