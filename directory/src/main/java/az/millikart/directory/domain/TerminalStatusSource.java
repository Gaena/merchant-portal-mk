package az.millikart.directory.domain;

// Кто последним поставил статус (Р-66). MANUAL — человек: ручную блокировку сверка не снимает никогда.
// PROVIDER — сверка: она же вернёт терминал в строй, иначе временное отключение у провайдера гасило
// бы его навсегда.
public enum TerminalStatusSource {
    MANUAL,
    PROVIDER
}
