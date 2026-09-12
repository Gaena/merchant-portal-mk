package az.millikart.ecom.dto;

import java.time.Instant;

/**
 * Терминал провайдера для формы заведения нашего.
 *
 * Логин здесь есть, пароля нет и не будет: пароль у провайдера мы не спрашиваем, его вводит
 * администратор при заведении терминала.
 */
public record ProviderTerminalResponse(
        String rid,
        String title,
        String login,
        boolean active,
        /** Когда терминал последний раз приходил в выгрузке. Пусто — не приходил ни разу. */
        Instant lastSeenAt
) {
}
