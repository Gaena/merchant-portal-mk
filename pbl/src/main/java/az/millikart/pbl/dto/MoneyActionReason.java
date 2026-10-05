package az.millikart.pbl.dto;

// Почему кнопка возврата или списания выключена (Р-123). Словарь экрана — types/transaction.ts; новое
// значение — туда же и во все три языка.
public enum MoneyActionReason {
    NO_RIGHTS,
    TERMINAL_NOT_IN_PORTAL,
    NO_PROVIDER_CREDENTIALS,
    FULLY_REFUNDED,
    CAPTURE_FIRST,
    ALREADY_CAPTURED,
    OUTCOME_UNKNOWN,
    IN_PROGRESS
}
