package az.millikart.auth.dto;

// Ответ входа и refresh; сроки — в секундах. refreshToken одноразовый. sessionExpiresIn — до конца сессии, после
// него /refresh отказывает (Р-140). Без токенов: passwordChangeRequired — пароль задан не владельцем, сессию даёт
// /change-password (Р-100); mfaRequired — нужен код второго фактора по mfaToken, mfaSetupRequired — фактор ещё не
// подключён (Р-141).
public record LoginResponse(
        String token,
        long expiresIn,
        String role,
        String refreshToken,
        long refreshExpiresIn,
        long sessionExpiresIn,
        boolean passwordChangeRequired,
        boolean mfaRequired,
        boolean mfaSetupRequired,
        String mfaToken
) {

    public static LoginResponse passwordChangeRequired(String role) {
        return new LoginResponse(null, 0, role, null, 0, 0, true, false, false, null);
    }

    public static LoginResponse secondFactorRequired(String role, String mfaToken, boolean setupRequired) {
        return new LoginResponse(null, 0, role, null, 0, 0, false, true, setupRequired, mfaToken);
    }

    // Токены — секреты: сгенерированный toString напечатал бы их в любой лог.
    @Override
    public String toString() {
        return "LoginResponse[token=" + (token == null ? null : "***") + ", expiresIn=" + expiresIn
                + ", role=" + role + ", refreshToken=" + (refreshToken == null ? null : "***")
                + ", refreshExpiresIn=" + refreshExpiresIn + ", sessionExpiresIn=" + sessionExpiresIn
                + ", passwordChangeRequired=" + passwordChangeRequired + ", mfaRequired=" + mfaRequired
                + ", mfaSetupRequired=" + mfaSetupRequired + ", mfaToken=" + (mfaToken == null ? null : "***") + "]";
    }
}
