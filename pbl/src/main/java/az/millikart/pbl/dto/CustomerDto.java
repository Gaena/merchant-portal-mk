package az.millikart.pbl.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record CustomerDto(
        @Size(max = 255, message = "customer.fullName must be at most 255 characters")
        String fullName,

        @Email(message = "customer.email must be a valid email address")
        @Size(max = 255, message = "customer.email must be at most 255 characters")
        String email,

        String phone
) {
}
