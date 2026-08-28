package az.millikart.auth.dto;

// Намеренно без валидации: logout отвечает 204 на что угодно — отдельный ответ на «неизвестный
// токен» сделал бы эндпоинт оракулом существования токенов.
public record LogoutRequest(
        String refreshToken
) {
}
