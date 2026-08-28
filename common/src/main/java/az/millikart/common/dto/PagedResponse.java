package az.millikart.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

// Единственный конверт постраничного ответа в проекте: эту форму возвращает каждый эндпойнт со
// списком, чтобы клиент листал транзакции и журнал аудита одинаково. Второго не заводить.
public record PagedResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int size,
        int number
) {

    public static <T> PagedResponse<T> of(Page<?> page, List<T> content) {
        return new PagedResponse<>(
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getSize(),
                page.getNumber()
        );
    }
}
