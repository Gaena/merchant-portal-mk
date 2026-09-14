package az.millikart.directory.dto;

import az.millikart.directory.domain.TerminalStatus;

// Р-45: лёгкий фид для GET /api/v1/terminals/options. status заполнен всегда, и эндпоинт отдаёт
// заблокированные терминалы тоже: форме ссылки нужны только ACTIVE, а экрану транзакций — все,
// иначе прошедшие через них платежи теряют имя.
//
// login здесь есть намеренно: мерчант опознаёт терминал по логину, а не по имени и не по числовому
// id, поэтому логин — то, что подписывает терминал на всех экранах платежей. Доступ к фиду ровно
// тот же, что к постраничному GET /api/v1/terminals, который логин отдаёт и так
// (TerminalService.isGlobalReader + requireOwnCompany на обоих), — новой видимости это не даёт,
// избавляет только от листания страниц ради одной подписи.
//
// password не будет здесь никогда: он и в полной карточке отдаётся замаскированным
// (TerminalService.mapToResponse). Поля, которого нет, не сольёт и неосторожный маппер.
public record TerminalOptionResponse(
        Integer id,
        String name,
        String login,
        TerminalStatus status
) {
}
