package az.millikart.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Код второго фактора по шагу входа (Р-141): принятый код выдаёт сессию.
public record MfaVerifyRequest(
        @NotBlank(message = "Sign-in step token is required")
        @Size(max = 128, message = "Sign-in step token is malformed")
        String mfaToken,
        @NotBlank(message = "Code is required")
        @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits")
        String code
) {

    @Override
    public String toString() {
        return "MfaVerifyRequest[mfaToken=***, code=***]";
    }
}
