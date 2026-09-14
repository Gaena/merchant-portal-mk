package az.millikart.ecom.dto;

// Терминал для фильтра выписки: наш терминал в скоупе пользователя и провайдерский за ним. У
// провайдера терминал и мерчант — одно, поэтому фильтр идёт по merchantRid. Название и логин —
// из слепка provider_terminals; пустые, если терминала в слепке нет.
public record EcomTerminalResponse(
        String merchantRid,
        String title,
        String login
) {
}
