package az.millikart.directory;

import static org.assertj.core.api.Assertions.assertThat;

import az.millikart.directory.repository.AuditLogQueryRepository;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

// Р-42 для читающей стороны журнала. Сторож в common видит только пишущий репозиторий, а этот наследовал
// JpaSpecificationExecutor с delete(Specification) — и мог стереть записи. Здесь — ровно один метод чтения.
class AuditLogQueryRepositoryReadOnlyTest {

    @Test
    void theOnlyMethodReadsAPage() throws NoSuchMethodException {
        assertThat(Arrays.stream(AuditLogQueryRepository.class.getMethods()).map(Method::getName))
                .containsExactly("findAll");
        assertThat(AuditLogQueryRepository.class.getMethod("findAll", Specification.class, Pageable.class)).isNotNull();
    }
}
