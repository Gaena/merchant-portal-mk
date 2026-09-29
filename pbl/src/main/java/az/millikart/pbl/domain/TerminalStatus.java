package az.millikart.pbl.domain;

// Колонку terminals пишет directory. BLOCKED запрещает только НОВЫЕ платежи (Р-38): в опрос статуса,
// списание DMS и возврат проверку не добавлять — она заперла бы деньги плательщика на карте.
public enum TerminalStatus {
    ACTIVE,
    BLOCKED
}
