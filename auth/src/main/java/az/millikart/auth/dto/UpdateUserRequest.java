package az.millikart.auth.dto;

import az.millikart.common.validation.ValidPassword;

public record UpdateUserRequest(
        String fullName,
        String role,
        @ValidPassword
        String password,
        String status,
        // null — не менять, пустая строка — снять (только ролям вне компании); менять может только
        // SYSTEM_ADMIN (Р-90).
        String companyId
) {
}
