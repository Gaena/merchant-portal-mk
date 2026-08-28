package az.millikart.directory.dto;

import az.millikart.directory.domain.TerminalStatus;

// Р-45: лёгкий фид для GET /api/v1/terminals/options. Полей ровно три: реквизитов эквайринга
// здесь нет вовсе, а не замаскированы — поле, которого нет, не сольёт неосторожный маппер.
// status заполнен всегда, и эндпоинт отдаёт заблокированные терминалы тоже: форме ссылки нужны
// только ACTIVE, а экрану транзакций — все, иначе прошедшие через них платежи теряют имя.
public record TerminalOptionResponse(
        Integer id,
        String name,
        TerminalStatus status
) {
}
