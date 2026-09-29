package az.millikart.directory.domain;

// Терминалы не удаляются, а блокируются: на них ссылаются платёжные ссылки (Р-37). BLOCKED
// останавливает только новые платежи — capture, возвраты и опрос статуса работают (Р-38). В MilliKart
// блокировка не уходит (AGENTS.md §10).
public enum TerminalStatus {
    ACTIVE,
    BLOCKED
}
