package az.millikart.ecom.dto;

import java.time.Instant;

// Пароля нет: у терминала его нет вовсе, к провайдеру ходят с кредами компании (Р-93).
public record ProviderTerminalResponse(
        String rid,
        String title,
        String login,
        // Номер терминала у провайдера — им терминал подписан на экранах (Р-96).
        String terminalRid,
        boolean active,
        // Когда терминал последний раз приходил в выгрузке; пусто — не приходил ни разу.
        Instant lastSeenAt
) {
}
