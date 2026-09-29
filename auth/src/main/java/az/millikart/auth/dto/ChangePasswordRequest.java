package az.millikart.auth.dto;

import az.millikart.common.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// Смена пароля без сессии: ею кончается обязательная смена при первом входе (Р-100).
public record ChangePasswordRequest(
        @NotBlank(message = "Username is required")
        @Email(message = "Username must be a valid email address")
        String username,
        @NotBlank(message = "Current password is required")
        String currentPassword,
        @NotBlank(message = "New password is required")
        @ValidPassword
        String newPassword
) {
    @Override
    public String toString() {
        return "ChangePasswordRequest[username=" + username + ", currentPassword=***, newPassword=***]";
    }
}
