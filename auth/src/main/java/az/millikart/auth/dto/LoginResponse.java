package az.millikart.auth.dto;

// Ответ и входа, и refresh. expiresIn и refreshExpiresIn — секунды, выведенные из
// pbl.security.jwt.expiration-ms и auth.refresh.ttl, не константы. refreshToken одноразовый: каждый
// refresh выдаёт новый и отставляет предъявленный. passwordChangeRequired — пароль верен, но задан не
// владельцем: токенов нет, сессию даёт только /change-password (PCI DSS 8.3.5, Р-100).
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
