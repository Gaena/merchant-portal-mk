package az.millikart.pbl.dto;

import jakarta.validation.constraints.Email;

public record CustomerDto(
        String fullName,

        @Email(message = "customer.email must be a valid email address")
        String email,

        String phone
) {
}
