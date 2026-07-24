package az.millikart.auth.dto;

import az.millikart.common.validation.ValidPassword;

public record UpdateUserRequest(
        String fullName,
        String role,
        @ValidPassword
        String password,
        String status
) {
}
