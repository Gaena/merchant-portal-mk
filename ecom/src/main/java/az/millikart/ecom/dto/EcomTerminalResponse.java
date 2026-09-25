package az.millikart.ecom.dto;

// Мерчант скоупа для фильтра выписки (Р-97). У провайдера терминал и мерчант — одно, поэтому фильтр идёт
// по merchantRid. Номер терминала, логин и название — из слепка provider_terminals; мерчанта, которого
// в нём нет, название — из слепка логинов, а номера и логина нет.
public record EcomTerminalResponse(
        String merchantRid,
        String title,
        String login,
        String terminalRid
) {
}
