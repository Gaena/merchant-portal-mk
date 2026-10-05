package az.millikart.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.hibernate.annotations.Immutable;

// Р-42: журнал append-only, и первая половина правила держится в Java — у репозитория нет
// способа удалить или заменить запись, у сущности нет способа изменить прочитанную. Вторая
// половина — грант в базе (project_docs/guides/deployment_guide.md), бесполезный, пока сервисы ходят под postgres
// (AGENTS.md §10). Отменяется всё это случайно: extends JpaRepository "ради findAll" дарит deleteAll.
@DisplayName("the audit journal cannot be edited or deleted from the application (Р-42)")
class AuditLogAppendOnlyTest {

    // Ровно один метод: любое наследование (CrudRepository, JpaRepository) добавило бы delete*, saveAll, flush.
    @Test
    void repositoryExposesNothingButSave() {
        List<String> methods = Arrays.stream(AuditLogRepository.class.getMethods())
                .map(Method::getName)
                .toList();

        assertThat(methods)
                .as("the write side needs exactly one method, and it adds a row")
                .containsExactly("save");
    }

    // Прочитанную запись нельзя изменять. Без этого Setter вернулся бы в AuditLog при рефакторинге,
    // и журнал стал бы редактируемым через любой репозиторий, умеющий save, — включая тот
    // единственный метод, что есть у пишущей стороны. Ничто другое от такой правки не упало бы.
    @Test
    void entityHasNoSetters() {
        List<String> setters = Arrays.stream(AuditLog.class.getMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("set"))
                .toList();

        assertThat(setters).isEmpty();
    }

    // Единственный save пишущей стороны на записи с id существующей делает merge — и переписывает её.
    // Поэтому id и время не задаются снаружи, а @Immutable не даёт Hibernate выпустить UPDATE вовсе.
    @Test
    void builderCannotChooseTheIdOrTheTime() {
        List<String> builderMethods = Arrays.stream(AuditLog.AuditLogBuilder.class.getMethods())
                .map(Method::getName)
                .toList();

        assertThat(builderMethods).doesNotContain("id", "createdAt");
    }

    @Test
    void entityIsImmutableForHibernate() {
        assertThat(AuditLog.class.isAnnotationPresent(Immutable.class)).isTrue();
    }
}
