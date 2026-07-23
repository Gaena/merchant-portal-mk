package az.millikart.pbl.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Generic paginated response wrapper mirroring the structure of a Spring Data {@link Page}.
 */
public record PagedResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int size,
        int number
) {

    /**
     * Builds a {@link PagedResponse} from a Spring Data {@link Page} of mapped content.
     */
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
