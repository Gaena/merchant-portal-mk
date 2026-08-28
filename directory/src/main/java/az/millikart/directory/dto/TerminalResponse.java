package az.millikart.directory.dto;

import az.millikart.directory.domain.TerminalStatus;
import java.time.Instant;

public record TerminalResponse(
        Integer id,
        String name,
        String login,
        String password,
        String companyId,
        TerminalStatus status,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt
) {
}
