package az.millikart.auth.dto;

// Ответ входа и refresh; сроки — в секундах. refreshToken одноразовый. passwordChangeRequired —
// пароль задан не владельцем: токенов нет, сессию даёт только /change-password (Р-100).
public record LoginResponse(
        String token,
        long expiresIn,
        String role,
        String refreshToken,
        long refreshExpiresIn,
        boolean passwordChangeRequired
) {

    public static LoginResponse passwordChangeRequired(String role) {
        return new LoginResponse(null, 0, role, null, 0, true);
    }

    // Токены — секреты: сгенерированный toString напечатал бы их в любой лог.
    @Override
    public String toString() {
        return "LoginResponse[token=" + (token == null ? null : "***") + ", expiresIn=" + expiresIn
                + ", role=" + role + ", refreshToken=" + (refreshToken == null ? null : "***")
                + ", refreshExpiresIn=" + refreshExpiresIn + ", passwordChangeRequired=" + passwordChangeRequired + "]";
    }
}
