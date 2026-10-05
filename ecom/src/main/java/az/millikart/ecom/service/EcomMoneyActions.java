package az.millikart.ecom.service;

import static az.millikart.common.money.MoneyActionReason.ALREADY_CAPTURED;
import static az.millikart.common.money.MoneyActionReason.CAPTURE_FIRST;
import static az.millikart.common.money.MoneyActionReason.FULLY_REFUNDED;
import static az.millikart.common.money.MoneyActionReason.IN_PROGRESS;
import static az.millikart.common.money.MoneyActionReason.NO_PROVIDER_CREDENTIALS;
import static az.millikart.common.money.MoneyActionReason.NO_RIGHTS;
import static az.millikart.common.money.MoneyActionReason.OUTCOME_UNKNOWN;
import static az.millikart.common.money.MoneyActionReason.TERMINAL_NOT_IN_PORTAL;

import az.millikart.common.money.MoneyActionRoles;
import az.millikart.common.money.OperationActions;
import az.millikart.common.money.OperationActions.Action;
import az.millikart.common.security.Role;
import az.millikart.ecom.domain.ProviderOrderAttempt;
import az.millikart.ecom.service.EcomStatusResolver.EcomStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

// Правила кнопок заказа выписки (Р-124) — те же, что у операции портала (pbl MoneyActions, Р-123), на статусах
// выписки. Деньги — по правилам выписки (EcomOrderAssembler.money): captured уже за вычетом реверсалов.
final class EcomMoneyActions {

    // Возврат имеет смысл у оплаченного (и частично) и у холда — тогда «сначала спишите».
    private static final Set<EcomStatus> REFUND_VISIBLE = EnumSet.of(EcomStatus.SUCCESS, EcomStatus.PARTIALLY_PAID,
            EcomStatus.PARTIALLY_REFUNDED, EcomStatus.REFUNDED, EcomStatus.AUTHORIZED);

    // Списание — только у DMS. Холд — AUTHORIZED: у DMS со списанием статус идёт по деньгам (Р-92).
    private static final Set<EcomStatus> CAPTURE_VISIBLE = EnumSet.of(EcomStatus.AUTHORIZED, EcomStatus.SUCCESS,
            EcomStatus.PARTIALLY_PAID, EcomStatus.PARTIALLY_REFUNDED, EcomStatus.REFUNDED);

    private EcomMoneyActions() {
    }

    // terminalKnown — мерчант заказа заведён терминалом портала; credentialsPresent — у компании терминала есть креды.
    record Facts(EcomStatus status, boolean dms, BigDecimal amount, BigDecimal captured, BigDecimal refunded,
                 Role role, boolean terminalKnown, boolean credentialsPresent, ProviderOrderAttempt attempt,
                 Instant now) {
    }

    static OperationActions decide(Facts facts) {
        return new OperationActions(refund(facts), capture(facts), unresolved(facts));
    }

    private static Action refund(Facts facts) {
        if (!REFUND_VISIBLE.contains(facts.status())) {
            return null;
        }
        if (facts.role() == null || !MoneyActionRoles.REFUND.contains(facts.role())) {
            return Action.disabled(NO_RIGHTS);
        }
        if (facts.attempt() != null) {
            return Action.disabled(facts.attempt().outcomeUnknown(facts.now()) ? OUTCOME_UNKNOWN : IN_PROGRESS);
        }
        if (facts.status() == EcomStatus.AUTHORIZED) {
            return Action.disabled(CAPTURE_FIRST);
        }
        BigDecimal left = orZero(facts.captured()).subtract(orZero(facts.refunded()));
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
        if (facts.role() == null || !MoneyActionRoles.CAPTURE.contains(facts.role())) {
            return Action.disabled(NO_RIGHTS);
        }
        if (facts.attempt() != null) {
            return Action.disabled(facts.attempt().outcomeUnknown(facts.now()) ? OUTCOME_UNKNOWN : IN_PROGRESS);
        }
        if (facts.status() != EcomStatus.AUTHORIZED || facts.amount() == null) {
            return Action.disabled(ALREADY_CAPTURED);
        }
        Action unreachable = providerUnreachable(facts);
        return unreachable != null ? unreachable : Action.enabled(facts.amount().subtract(orZero(facts.captured())));
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

    private static OperationActions.Unresolved unresolved(Facts facts) {
        ProviderOrderAttempt attempt = facts.attempt();
        if (attempt == null) {
            return null;
        }
        boolean unknown = attempt.outcomeUnknown(facts.now());
        return new OperationActions.Unresolved(attempt.getKind().name(), attempt.getAmount(),
                (unknown ? ProviderOrderAttempt.State.UNKNOWN : ProviderOrderAttempt.State.IN_PROGRESS).name(),
                attempt.getStartedAt(), attempt.getStartedBy(), unknown && facts.role() == Role.SYSTEM_ADMIN);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
