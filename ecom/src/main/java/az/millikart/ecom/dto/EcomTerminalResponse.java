package az.millikart.ecom.dto;

// Мерчант скоупа для фильтра выписки (Р-97). Мерчанта без строки в provider_terminals название — из
// слепка логинов, номера и логина у него нет.
public record EcomTerminalResponse(
        String merchantRid,
        String title,
        String login,
        String terminalRid
) {
}
