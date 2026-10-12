package az.millikart.common.money;

// Почему кнопка возврата или списания выключена (Р-123). Один словарь на pbl и ecom: экран у них общий
// (types/transaction.ts); новое значение — туда же и во все три языка.
public enum MoneyActionReason {
    NO_RIGHTS,
    TERMINAL_NOT_IN_PORTAL,
    NO_PROVIDER_CREDENTIALS,
    FULLY_REFUNDED,
    CAPTURE_FIRST,
    ALREADY_CAPTURED,
    // Оплаченный DMS без списанной суммы (Р-139), только pbl.
    CAPTURED_UNKNOWN,
    OUTCOME_UNKNOWN,
    IN_PROGRESS
}
