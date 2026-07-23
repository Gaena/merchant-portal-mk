package az.millikart.pbl.dto;

import jakarta.validation.constraints.Email;

/**
 * Customer information associated with a payment link.
 */
public record CustomerDto(
        String fullName,

        @Email(message = "customer.email must be a valid email address")
        String email,

        String phone
) {
}
