package az.millikart.directory.dto;

import az.millikart.directory.domain.TerminalStatus;

// Логина нет: его меняет только сверка со справочником (Р-67). Смена status трогает и платёжные
// ссылки терминала (Р-39, Р-40).
public record UpdateTerminalRequest(
        String name,
        String companyId,
        TerminalStatus status
) {
}
