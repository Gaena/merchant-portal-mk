package az.millikart.common.money;

import java.math.BigDecimal;
import java.time.Instant;

// Денежные действия карточки операции портала или заказа выписки (Р-123, Р-124): видна ли кнопка, активна
// ли и почему нет. Экран правил не повторяет — только показывает. null у действия — кнопки нет по смыслу (у
// SMS нет списания, у отклонённой операции нет ничего). Только в ответе одной операции: списки его не несут.
public record OperationActions(
        Action refund,
        Action capture,
        // Неподтверждённая денежная операция: пока она есть, обе кнопки выключены.
        Unresolved unresolved
) {

    // reason — код из MoneyActionReason, null у активной; maxAmount — потолок суммы, только у активной.
    public record Action(boolean enabled, String reason, BigDecimal maxAmount) {

        public static Action enabled(BigDecimal maxAmount) {
            return new Action(true, null, maxAmount);
        }

        public static Action disabled(MoneyActionReason reason) {
            return new Action(false, reason.name(), null);
        }
    }

    // state — IN_PROGRESS или UNKNOWN; resolvable — может ли смотрящий разрешить исход (SYSTEM_ADMIN, UNKNOWN).
    public record Unresolved(String kind, BigDecimal amount, String state, Instant startedAt, String startedBy,
                             boolean resolvable) {
    }
}
