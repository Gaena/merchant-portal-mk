package az.millikart.ecom.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Что спросили у выписки. merchantRids кладёт сервис, а не контроллер: это скоуп из терминалов
// компании, суженный фильтром запроса, и шире скоупа он не бывает. Пустой — выписка пустая.
public record EcomTransactionFilter(
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
