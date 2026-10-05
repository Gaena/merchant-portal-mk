package az.millikart.pbl.service;

import static az.millikart.pbl.dto.MoneyActionReason.ALREADY_CAPTURED;
import static az.millikart.pbl.dto.MoneyActionReason.CAPTURE_FIRST;
import static az.millikart.pbl.dto.MoneyActionReason.FULLY_REFUNDED;
import static az.millikart.pbl.dto.MoneyActionReason.IN_PROGRESS;
import static az.millikart.pbl.dto.MoneyActionReason.NO_PROVIDER_CREDENTIALS;
import static az.millikart.pbl.dto.MoneyActionReason.NO_RIGHTS;
import static az.millikart.pbl.dto.MoneyActionReason.OUTCOME_UNKNOWN;
import static az.millikart.pbl.dto.MoneyActionReason.TERMINAL_NOT_IN_PORTAL;

import az.millikart.common.security.Role;
import az.millikart.pbl.domain.MoneyOperationAttempt;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.pbl.dto.TransactionActions;
import az.millikart.pbl.dto.TransactionActions.Action;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

// Правила кнопок возврата и списания (Р-123): видна по смыслу, активна — когда сервис примет действие, иначе —
// причина. refund и completeDms проверяют то же самое ещё раз; меняешь проверку там — меняй и здесь.
final class MoneyActions {

    // Возврат имеет смысл у оплаченного и у холда (тогда — «сначала спишите»); у неоплаченного и отклонённого нет.
    private static final Set<TransactionStatus> REFUND_VISIBLE = EnumSet.of(TransactionStatus.SUCCESS,
            TransactionStatus.PARTIALLY_REFUNDED, TransactionStatus.REFUNDED, TransactionStatus.AUTHORIZED);

    // Списание — только у DMS. PENDING — тоже: копия может отставать от холда, списание сначала спросит эквайера (P0-2).
    private static final Set<TransactionStatus> CAPTURE_VISIBLE = EnumSet.of(TransactionStatus.PENDING,
            TransactionStatus.AUTHORIZED, TransactionStatus.SUCCESS, TransactionStatus.PARTIALLY_REFUNDED,
            TransactionStatus.REFUNDED);

    private static final Set<TransactionStatus> CAPTURABLE = EnumSet.of(TransactionStatus.PENDING, TransactionStatus.AUTHORIZED);

    private MoneyActions() {
    }

    // terminalKnown — терминал операции заведён в портале; credentialsPresent — у его компании есть креды (Р-93).
    record Facts(TransactionStatus status, boolean dms, BigDecimal amount, BigDecimal capturedAmount,
                 BigDecimal refundedAmount, Role role, boolean terminalKnown, boolean credentialsPresent,
                 MoneyOperationAttempt attempt, Instant now) {
    }

    static TransactionActions decide(Facts facts) {
        return new TransactionActions(refund(facts), capture(facts), unresolved(facts));
    }

    private static Action refund(Facts facts) {
        if (!REFUND_VISIBLE.contains(facts.status())) {
            return null;
        }
        if (facts.role() == null || !PaymentLinkService.REFUND_ROLES.contains(facts.role())) {
            return Action.disabled(NO_RIGHTS);
        }
        if (facts.attempt() != null) {
            return Action.disabled(facts.attempt().outcomeUnknown(facts.now()) ? OUTCOME_UNKNOWN : IN_PROGRESS);
        }
        if (facts.status() == TransactionStatus.AUTHORIZED) {
            return Action.disabled(CAPTURE_FIRST);
        }
        BigDecimal base = facts.capturedAmount() != null ? facts.capturedAmount() : facts.amount();
        BigDecimal left = base.subtract(facts.refundedAmount() != null ? facts.refundedAmount() : BigDecimal.ZERO);
        if (left.signum() <= 0) {
            return Action.disabled(FULLY_REFUNDED);
        }
        Action unreachable = providerUnreachable(facts);
        return unreachable != null ? unreachable : Action.enabled(left);
    }

    private static Action capture(Facts facts) {
        if (!facts.dms() || !CAPTURE_VISIBLE.contains(facts.status())) {
            return null;
        }
        if (facts.role() == null || !PaymentLinkService.LINK_WRITE_ROLES.contains(facts.role())) {
            return Action.disabled(NO_RIGHTS);
        }
        if (facts.attempt() != null) {
            return Action.disabled(facts.attempt().outcomeUnknown(facts.now()) ? OUTCOME_UNKNOWN : IN_PROGRESS);
        }
        if (!CAPTURABLE.contains(facts.status())) {
            return Action.disabled(ALREADY_CAPTURED);
        }
        Action unreachable = providerUnreachable(facts);
        return unreachable != null ? unreachable : Action.enabled(facts.amount());
    }

    private static Action providerUnreachable(Facts facts) {
        if (!facts.terminalKnown()) {
            return Action.disabled(TERMINAL_NOT_IN_PORTAL);
        }
        if (!facts.credentialsPresent()) {
            return Action.disabled(NO_PROVIDER_CREDENTIALS);
        }
        return null;
    }

    private static TransactionActions.Unresolved unresolved(Facts facts) {
        MoneyOperationAttempt attempt = facts.attempt();
        if (attempt == null) {
            return null;
        }
        boolean unknown = attempt.outcomeUnknown(facts.now());
        return new TransactionActions.Unresolved(attempt.getKind().name(), attempt.getAmount(),
                (unknown ? MoneyOperationAttempt.State.UNKNOWN : MoneyOperationAttempt.State.IN_PROGRESS).name(),
                attempt.getStartedAt(), attempt.getStartedBy(), unknown && facts.role() == Role.SYSTEM_ADMIN);
    }
}
