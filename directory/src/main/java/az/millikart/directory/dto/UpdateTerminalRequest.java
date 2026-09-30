package az.millikart.directory.dto;

import az.millikart.directory.domain.TerminalStatus;
import jakarta.validation.constraints.Size;

// Логина нет: его меняет только сверка со справочником (Р-67). Смена status трогает и платёжные
// ссылки терминала (Р-39, Р-40).
public record UpdateTerminalRequest(
        @Size(max = 255, message = "Terminal name must be at most 255 characters")
        String name,
        String companyId,
        TerminalStatus status
) {
}
