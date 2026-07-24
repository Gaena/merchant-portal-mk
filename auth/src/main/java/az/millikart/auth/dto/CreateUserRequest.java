package az.millikart.auth.dto;

import az.millikart.common.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank(message = "Username is required")
        @Email(message = "Username must be a valid email address")
        String username,

        @NotBlank(message = "Password is required")
        @ValidPassword
        String password,

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Role is required")
        String role,

        String companyId
) {
}
