package az.millikart.ecom.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Что мерчант спросил у выписки.
 *
 * `merchantRids` сюда кладёт сервис, а не контроллер: это не фильтр, а скоуп, и приходит он из
 * таблицы привязок, а не из запроса. Пустой список означает «показывать нечего» — см.
 * `EcomTransactionService`.
 *
 * Период обязателен. Выписка «за всё время» по операционной базе шлюза — это полный скан ради
 * экрана, на котором мерчант всё равно смотрит последние дни.
 */
public record EcomTransactionFilter(
        List<String> merchantRids,
        Instant dateFrom,
        Instant dateTo,
        List<String> terminalIds,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        /** Поиск по номеру заказа, ссылке мерчанта, RRN и почте плательщика. */
        String query,
        /** Курсор предыдущей страницы или `null` для первой. */
        String cursor,
        int pageSize
) {
}
