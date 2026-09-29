package az.millikart.ecom.dto;

import az.millikart.ecom.service.EcomPaymentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// merchantRids кладёт сервис, а не контроллер: это скоуп, суженный фильтром, и пустым он сюда не
// приходит (Р-97).
public record EcomTransactionFilter(
        List<String> merchantRids,
        // Период по дате создания заказа, [dateFrom, dateTo) (Р-74).
        Instant dateFrom,
        Instant dateTo,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        // Номер заказа, ridByMerchant или RRN — точным совпадением.
        String query,
        // null — фильтра нет (Р-87). Статуса здесь нет: он считается в Java после сборки заказа.
        EcomPaymentType paymentType
) {
}
