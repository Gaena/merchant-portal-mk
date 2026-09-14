package az.millikart.directory.dto;

// Ответ GET /api/v1/terminals/{id}/password — единственное место во всём API, где пароль терминала
// уходит наружу как есть. Отдельный ответ, а не поле в TerminalResponse, намеренно: в карточке и
// в списке пароль остаётся замаскированным, и никакой неосторожный маппер не вынесет его в экран,
// который просто показывает терминалы. Читать может только SYSTEM_ADMIN, и каждое чтение
// ложится в журнал аудита — см. TerminalService.revealPassword.
public record TerminalPasswordResponse(
        Integer id,
        String password
) {
}
