package az.millikart.pbl.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Проверка учётных данных, которых ещё нет в базе: администратор заводит терминал и хочет
 * убедиться, что пароль верный, до того как сохранить его. Для уже заведённого терминала тело не
 * нужно — учётные данные берутся из базы по его номеру.
 */
public record TerminalCheckRequest(
        @NotBlank(message = "login is required")
        String login,

        @NotBlank(message = "password is required")
        String password
) {
}
