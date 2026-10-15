package az.millikart.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Подключение второго фактора по шагу входа, выданному после верного пароля (Р-141).
public record MfaSetupRequest(
        @NotBlank(message = "Sign-in step token is required")
        @Size(max = 128, message = "Sign-in step token is malformed")
        String mfaToken
) {

    // Токен шага — секрет: сгенерированный toString напечатал бы его в любой лог.
    @Override
    public String toString() {
        return "MfaSetupRequest[mfaToken=***]";
    }
}
