package az.millikart.directory.dto;

// Терминал провайдера для формы заведения терминала компании (Р-96): только мерчанты логина компании,
// ещё не заведённые у нас. rid — код мерчанта (merchantRid), terminalRid — номер терминала у провайдера.
public record ProviderTerminalOption(String rid, String title, String login, String terminalRid) {
}
