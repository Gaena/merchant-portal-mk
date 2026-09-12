package az.millikart.ecom.dto;

import java.math.BigDecimal;

/**
 * Карточки статистики над вкладкой.
 *
 * Считаются **на стороне базы по всему фильтру**, а не в памяти по загруженной странице. У
 * платёжных ссылок фронтенд складывает то, что уже получил, и это работает, пока строк
 * немного; здесь выписка за квартал не помещается ни в одну страницу, и «итого» по текущим
 * двадцати пяти строкам было бы неверным числом с правильной подписью.
 */
public record EcomStatsResponse(
        long orderCount,
        long successCount,
        long failedCount,
        long pendingCount,
        BigDecimal capturedAmount,
        BigDecimal refundedAmount,
        String currency
) {
}
