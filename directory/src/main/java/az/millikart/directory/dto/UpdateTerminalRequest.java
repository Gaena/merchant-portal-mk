package az.millikart.directory.dto;

import az.millikart.directory.domain.TerminalStatus;

// status — единственное поле этого PATCH с эффектом за пределами строки терминала: блокировка
// приостанавливает и активные платёжные ссылки терминала (Р-37).
public record UpdateTerminalRequest(
        String name,
        String login,
        String password,
        String companyId,
        TerminalStatus status
) {
}
