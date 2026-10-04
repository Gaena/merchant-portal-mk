package az.millikart.txpg.dto;

import java.math.BigDecimal;

// Заказ, который сервис просит завести у провайдера. Клиент не знает, из чего его собрали: ссылку в заказ
// переводит pbl (ProviderOrders). payer — клиент для 3DS (Р-96), null — блока tdsPresetAreq нет.
public record NewOrder(
        boolean dms,
        BigDecimal amount,
        String currency,
        String description,
        EcomCreateOrderRequest.TdsPresetAreq payer
) {
}
