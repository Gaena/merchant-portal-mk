package az.millikart.auth.dto;

import az.millikart.common.validation.ValidPassword;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 255, message = "Full name must be at most 255 characters")
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
