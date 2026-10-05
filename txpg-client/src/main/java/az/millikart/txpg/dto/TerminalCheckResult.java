package az.millikart.txpg.dto;

// Исход кнопки «Тест» (Р-103): OK — заказ заведён; INVALID_CREDENTIALS — InvalidLogin, неверные креды
// компании; REJECTED — ответ без заказа; UNREACHABLE — нет ответа или 5xx с пустым телом: о терминале
// это не говорит ничего, за «неверный пароль» его не выдавать.
public record TerminalCheckResult(Outcome outcome, String providerErrorCode, String providerMessage) {

    public enum Outcome {
        OK,
        INVALID_CREDENTIALS,
        REJECTED,
        UNREACHABLE
    }

    public static TerminalCheckResult ok() {
        return new TerminalCheckResult(Outcome.OK, null, null);
    }

    public static TerminalCheckResult unreachable(String message) {
        return new TerminalCheckResult(Outcome.UNREACHABLE, null, message);
    }
}
