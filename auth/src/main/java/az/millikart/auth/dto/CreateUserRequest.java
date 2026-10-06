package az.millikart.auth.dto;

import az.millikart.common.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateUserRequest(
        @NotBlank(message = "Username is required")
        @Email(message = "Username must be a valid email address")
        @Size(max = 255, message = "Username must be at most 255 characters")
        String username,

        @NotBlank(message = "Password is required")
        @ValidPassword
        String password,

        @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name must be at most 255 characters")
        String fullName,

        @NotBlank(message = "Role is required")
        String role,

        String companyId,

        // Р-131: терминалы сотрудника — обязательны ему и запрещены остальным ролям; только своей компании.
        @Size(max = 500, message = "At most 500 terminals can be assigned")
        List<@NotNull(message = "Terminal ID must not be null") Integer> terminalIds
) {
}
