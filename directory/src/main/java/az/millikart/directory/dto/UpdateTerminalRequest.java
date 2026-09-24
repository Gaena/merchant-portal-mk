package az.millikart.directory.dto;

import az.millikart.directory.domain.TerminalStatus;

// status — единственное поле этого PATCH с эффектом за пределами строки терминала: блокировка
// приостанавливает и активные платёжные ссылки терминала (Р-37). Логин не правится: его хозяин —
// справочник провайдера, и меняет его только сверка (Р-93).
public record UpdateTerminalRequest(
        String name,
        String companyId,
        TerminalStatus status
) {
}
