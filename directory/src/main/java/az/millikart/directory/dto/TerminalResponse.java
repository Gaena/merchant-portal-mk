package az.millikart.directory.dto;

import java.time.Instant;

public record TerminalResponse(
        Integer id,
        String name,
        String login,
        String password,
        String companyId,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt
) {
}
