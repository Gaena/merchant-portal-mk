package az.millikart.directory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// Три индекса changeset 004 обязаны существовать после миграции, и проверяются они по метаданным
// схемы, а не по учёту самого Liquibase: changeset, отмеченный как выполненный, но тихо
// пропущенный по preconditions, устроил бы DATABASECHANGELOG и оставил таблицу без индексов.
@SpringBootTest
public class AuditLogSchemaTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void auditLogIndexes_existAfterMigration() throws Exception {
        Map<String, List<String>> columnsByIndex = new HashMap<>();
        Map<String, List<String>> orderingByIndex = new HashMap<>();

        try (Connection connection = dataSource.getConnection();
             ResultSet indexInfo = connection.getMetaData()
                     .getIndexInfo(null, null, "AUDIT_LOGS", false, false)) {
            // JDBC отдаёт строки в порядке имени индекса и позиции, поэтому собранные списки
            // колонок идут в порядке определения индекса.
            while (indexInfo.next()) {
                String indexName = indexInfo.getString("INDEX_NAME");
                String columnName = indexInfo.getString("COLUMN_NAME");
                if (indexName == null || columnName == null) {
                    continue;
                }
                String key = indexName.toLowerCase(Locale.ROOT);
                columnsByIndex.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(columnName.toLowerCase(Locale.ROOT));
                orderingByIndex.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(indexInfo.getString("ASC_OR_DESC"));
            }
        }

        assertThat(columnsByIndex.get("idx_audit_logs_company_created"))
                .as("merchant query index (company_id, created_at desc)")
                .containsExactly("company_id", "created_at");
        assertThat(columnsByIndex.get("idx_audit_logs_entity"))
                .as("object history index (entity_type, entity_id)")
                .containsExactly("entity_type", "entity_id");
        assertThat(columnsByIndex.get("idx_audit_logs_created"))
                .as("administrator view index (created_at desc)")
                .containsExactly("created_at");

        assertThat(orderingByIndex.get("idx_audit_logs_company_created").get(1))
                .as("created_at must be descending in the company index")
                .isEqualTo("D");
        assertThat(orderingByIndex.get("idx_audit_logs_created").getFirst())
                .as("created_at must be descending in the administrator index")
                .isEqualTo("D");
    }
}
