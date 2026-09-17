package az.millikart.auth.dto;

import az.millikart.common.validation.ValidPassword;

public record UpdateUserRequest(
        String fullName,
        String role,
        @ValidPassword
        String password,
        String status,
        // Р-90: компания пользователя. null — не менять; пустая строка — снять компанию (только ролям
        // вне компании: SYSTEM_ADMIN, AUDITOR). Перевести в другую компанию может только SYSTEM_ADMIN.
        String companyId
) {
}
