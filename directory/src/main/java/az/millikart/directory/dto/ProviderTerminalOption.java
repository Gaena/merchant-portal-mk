package az.millikart.directory.dto;

// Для формы заведения (Р-96): rid — мерчант (merchantRid), terminalRid — номер терминала у провайдера.
public record ProviderTerminalOption(String rid, String title, String login, String terminalRid) {
}
