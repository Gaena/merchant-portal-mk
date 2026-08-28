package az.millikart.auth.dto;

// Ответ и входа, и refresh. expiresIn и refreshExpiresIn — секунды, выведенные из
// pbl.security.jwt.expiration-ms и auth.refresh.ttl, не константы. refreshToken одноразовый: каждый
// refresh выдаёт новый и отставляет предъявленный.
public record LoginResponse(
        String token,
        long expiresIn,
        String role,
        String refreshToken,
        long refreshExpiresIn
) {
}
