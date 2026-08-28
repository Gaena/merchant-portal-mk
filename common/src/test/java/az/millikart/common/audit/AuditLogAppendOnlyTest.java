package az.millikart.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Р-42: журнал append-only, и первая половина правила держится в Java — у репозитория нет
// способа удалить или заменить запись, у сущности нет способа изменить прочитанную. Вторая
// половина — грант в базе (deployment_guide.md), бесполезный, пока сервисы ходят под postgres
// (problems.md). Отменяется всё это случайно: extends JpaRepository "ради findAll" дарит deleteAll.
@DisplayName("the audit journal cannot be edited or deleted from the application (Р-42)")
class AuditLogAppendOnlyTest {

    // Всё, чем можно удалить запись или переписать её.
    private static final List<String> FORBIDDEN_PREFIXES =
            List.of("delete", "remove", "update", "set", "truncate", "saveall", "flush");

    @Test
    void repositoryExposesNothingButSave() {
        List<String> methods = Arrays.stream(AuditLogRepository.class.getMethods())
                .map(Method::getName)
                .toList();

        assertThat(methods)
                .as("the write side needs exactly one method, and it adds a row")
                .containsExactly("save");
    }

    @Test
    void repositoryHasNoInheritedWayToRemoveOrRewriteRecords() {
        List<String> offenders = Arrays.stream(AuditLogRepository.class.getMethods())
                .map(Method::getName)
                .filter(name -> FORBIDDEN_PREFIXES.stream()
                        .anyMatch(prefix -> name.toLowerCase(Locale.ROOT).startsWith(prefix)))
                .filter(name -> !"save".equals(name))
                .toList();

        assertThat(offenders)
                .as("extending CrudRepository/JpaRepository would silently add these")
                .isEmpty();
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
}
