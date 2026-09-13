package az.millikart.pbl.provider.dto;

/**
 * Исход проверки учётных данных терминала у провайдера.
 *
 * Четыре исхода, и различать их обязательно — у каждого свой следующий шаг для администратора:
 *
 *   OK                   — логин и пароль верны, и терминалу разрешено принимать оплаты.
 *   INVALID_CREDENTIALS  — провайдер ответил `InvalidLogin`: неверный логин или пароль.
 *   REJECTED             — учётные данные провайдер принял, но заказ завести отказался:
 *                          терминал заблокирован, не допущен к оплатам и т. п. Текст — от него.
 *   UNREACHABLE          — ответа не получили или получили 5xx. О терминале это не говорит
 *                          ничего, и выдать такой исход за «пароль неверный» нельзя.
 */
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
