package az.millikart.directory.dto;

import java.time.Instant;

public record CompanyResponse(
        String id,
        String name,
        String status,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt
) {
}
