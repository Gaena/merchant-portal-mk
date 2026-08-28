package az.millikart.directory.domain;

// Р-37: терминалы не удаляются — одна платёжная ссылка держит строку через
// fk_payment_links_terminal навсегда, и «вывести терминал из эксплуатации» здесь значит
// заблокировать. BLOCKED останавливает только новые платежи (Р-38): опрос статуса, DMS-capture
// и возвраты по уже прошедшим работают. Блокировка локальна для портала (problems.md).
public enum TerminalStatus {
    ACTIVE,
    BLOCKED
}
