package az.millikart.auth.dto;

public record LoginResponse(
        String token,
        long expiresIn,
        String role
) {
}
