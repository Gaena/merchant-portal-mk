package az.millikart.directory.dto;

import java.time.Instant;

public record CompanyResponse(
        String id,
        String name,
        String status,
        // Только для SYSTEM_ADMIN, остальным — null (Р-93). Пароля в ответе нет вовсе.
        String providerLogin,
        // VÖEN (Р-129) — открытый реквизит, его печатает чек; null, пока не задан.
        String taxId,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt
) {
}
