package az.millikart.pbl.provider.dto;

/**
 * Исход кнопки «Тест»: можно ли создать платёж на терминале с кредами его компании (Р-93).
 *
 * Четыре исхода, и различать их обязательно — у каждого свой следующий шаг для администратора:
 *
 *   OK                   — креды компании верны, и провайдер завёл пробный заказ.
 *   INVALID_CREDENTIALS  — провайдер ответил `InvalidLogin`: неверный логин или пароль компании.
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
