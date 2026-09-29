package az.millikart.pbl.provider.dto;

// Исход кнопки «Тест» (Р-93, Р-103), у каждого свой следующий шаг администратора: OK — провайдер завёл
// пробный заказ; INVALID_CREDENTIALS — InvalidLogin, неверные креды компании; REJECTED — провайдер ответил,
// но заказ не создал, текст от него; UNREACHABLE — нет ответа или 5xx с пустым телом: о терминале это не
// говорит ничего, и выдать такой исход за «пароль неверный» нельзя.
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
