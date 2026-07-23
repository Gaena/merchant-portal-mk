package az.millikart.auth.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        String fullName,
        String role,
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password,
        String status
) {
}
