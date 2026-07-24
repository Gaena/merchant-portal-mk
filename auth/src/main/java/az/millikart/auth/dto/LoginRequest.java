package az.millikart.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Username is required")
        @Email(message = "Username must be a valid email address")
        String username,
        @NotBlank(message = "Password is required")
        String password
) {
}
