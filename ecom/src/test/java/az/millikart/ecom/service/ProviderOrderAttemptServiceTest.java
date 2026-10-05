package az.millikart.ecom.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import az.millikart.ecom.domain.ProviderOrderAttempt;
import az.millikart.ecom.dto.EcomOperationResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

// Решение 6 к MONEY-ACTIONS-ALL: неизвестный исход снимается сам, только когда выписка показывает ту самую
// операцию — одобренную, того же вида и суммы и не раньше отправки. Иначе это чужая операция, а запрет держится.
class ProviderOrderAttemptServiceTest {

    private static final Instant SENT = Instant.parse("2026-10-05T10:00:00Z");

    @Test
    void anApprovedRefundOfTheSameAmountAfterTheAttempt_isTheAttempt() {
        assertTrue(ProviderOrderAttemptService.seenInStatement(refundAttempt(),
                List.of(operation("REFUND", "Approved", SENT.plusSeconds(20), "25.00"))));
        // Часы шлюза могут отставать от наших — минута допуска.
        assertTrue(ProviderOrderAttemptService.seenInStatement(refundAttempt(),
                List.of(operation("REFUND", "Approved", SENT.minusSeconds(30), "25.00"))));
    }

    @Test
    void anythingElse_isNotTheAttempt() {
        assertFalse(ProviderOrderAttemptService.seenInStatement(refundAttempt(),
                List.of(operation("REFUND", "Declined", SENT.plusSeconds(20), "25.00"))), "declined");
        assertFalse(ProviderOrderAttemptService.seenInStatement(refundAttempt(),
                List.of(operation("REFUND", "Approved", SENT.plusSeconds(20), "24.99"))), "another amount");
        assertFalse(ProviderOrderAttemptService.seenInStatement(refundAttempt(),
                List.of(operation("REFUND", "Approved", SENT.minusSeconds(3600), "25.00"))), "an earlier refund");
        assertFalse(ProviderOrderAttemptService.seenInStatement(refundAttempt(),
                List.of(operation("CAPTURE", "Approved", SENT.plusSeconds(20), "25.00"))), "another kind");
    }

    private static ProviderOrderAttempt refundAttempt() {
        return ProviderOrderAttempt.builder().orderId("175900").kind(ProviderOrderAttempt.Kind.REFUND)
                .amount(new BigDecimal("25.00")).state(ProviderOrderAttempt.State.UNKNOWN)
                .startedBy("head@comp1.com").startedAt(SENT).build();
    }

    private static EcomOperationResponse operation(String kind, String result, Instant at, String amount) {
        return new EcomOperationResponse("TR-1", at, kind, "Refund", "Single", null, null, result,
                new BigDecimal(amount), new BigDecimal(amount).negate(), "AZN", null);
    }
}
