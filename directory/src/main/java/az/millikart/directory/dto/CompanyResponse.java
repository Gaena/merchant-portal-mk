package az.millikart.directory.dto;

import java.time.Instant;

public record CompanyResponse(
        String id,
        String name,
        String status,
        // Только для SYSTEM_ADMIN, остальным — null (Р-93). Пароля в ответе нет вовсе.
        String providerLogin,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt
) {
}
