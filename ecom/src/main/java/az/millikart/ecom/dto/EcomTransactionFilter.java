package az.millikart.ecom.dto;

import az.millikart.ecom.service.EcomPaymentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Что спросили у выписки. merchantRids кладёт сервис, а не контроллер: это скоуп (Р-97), суженный
// фильтром пользователя, и шире скоупа он не бывает. Пустым сюда не приходит — пустую выписку сервис
// отдаёт сам, без похода в шлюз.
public record EcomTransactionFilter(
        // Мерчанты, чьи заказы читаются.
        List<String> merchantRids,
        // Период по дате создания заказа, [dateFrom, dateTo) (Р-74).
        Instant dateFrom,
        Instant dateTo,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        // Номер заказа, ridByMerchant или RRN — точным совпадением.
        String query,
        // SMS или DMS — по операциям заказа (Р-87); null — фильтра нет. Статуса здесь нет намеренно:
        // он считается в Java после сборки заказа, и в SQL его не передать (EcomTransactionService).
        EcomPaymentType paymentType
) {
}
