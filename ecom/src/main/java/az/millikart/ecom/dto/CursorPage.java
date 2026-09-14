package az.millikart.ecom.dto;

import java.util.List;

// Страница с курсором вместо номера: OFFSET по операционной базе шлюза дорожает с каждой
// страницей и между запросами пропускает или повторяет новые строки. Курсор — номер последнего
// отданного заказа; nextCursor пуст на последней странице. Общего числа строк нет — это /stats.
public record CursorPage<T>(
        List<T> content,
        String nextCursor
) {
}
