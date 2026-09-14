package az.millikart.ecom.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Что спросили у выписки. logins и merchantRids кладёт сервис, а не контроллер: это скоуп из
// терминалов компании, и шире скоупа он не бывает. Пустой logins — выписка пустая.
public record EcomTransactionFilter(
        // Логины терминалов скоупа у шлюза.
        List<String> logins,
        // Фильтр по терминалу провайдера, уже суженный скоупом; null — фильтра нет.
        List<String> merchantRids,
        // Период по дате создания заказа, [dateFrom, dateTo) (Р-74).
        Instant dateFrom,
        Instant dateTo,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        // Номер заказа, ridByMerchant или RRN — точным совпадением.
        String query
) {
}
