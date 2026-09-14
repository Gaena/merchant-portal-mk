package az.millikart.ecom.dto;

import java.time.Instant;

// Терминал провайдера для формы заведения нашего. Пароля нет и не будет: у провайдера его не
// спрашивают, его вводит администратор.
public record ProviderTerminalResponse(
        String rid,
        String title,
        String login,
        boolean active,
        // Когда терминал последний раз приходил в выгрузке; пусто — не приходил ни разу.
        Instant lastSeenAt
) {
}
