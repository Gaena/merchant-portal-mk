package az.millikart.pbl;

import az.millikart.common.testing.PostgresTestContainer;
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
import org.springframework.context.annotation.Import;

// Три индекса из changeset 007 обязаны существовать после миграции; проверяем по метаданным самой
// схемы, а не по учёту Liquibase: changeset, помеченный как выполненный, но тихо пропущенный
// предусловием, удовлетворил бы DATABASECHANGELOG и оставил таблицу без индексов. Та же логика,
// что у AuditLogSchemaTest в directory.
//
// На настоящей PostgreSQL, а не на H2: смысл теста в том, что миграция создаёт индексы там, где
// они будут в проде. H2 в режиме совместимости — эмуляция, и её метаданные отвечают за свою схему,
// а не за нашу.
@SpringBootTest
@Import(PostgresTestContainer.class)
public class TransactionIndexSchemaTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void transactionIndexes_existAfterMigration() throws Exception {
        Map<String, List<String>> columnsByIndex = new HashMap<>();
        Map<String, List<String>> orderingByIndex = new HashMap<>();

        try (Connection connection = dataSource.getConnection();
             ResultSet indexInfo = connection.getMetaData()
                     // Имя в нижнем регистре: PostgreSQL складывает неэкранированные
                     // идентификаторы именно так, и метаданные отдают их в том же виде.
                     // С H2 здесь стояло "TRANSACTIONS" — первое же расхождение, которое
                     // видно после переезда на настоящую СУБД.
                     .getIndexInfo(null, null, "transactions", false, false)) {
            // JDBC отдаёт строки по имени индекса и порядковой позиции, поэтому собранные здесь
            // списки колонок идут в порядке определения индекса.
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

        assertThat(columnsByIndex.get("idx_transactions_link_status"))
                .as("attempt counting index (link_id, status)")
                .containsExactly("link_id", "status");
        assertThat(columnsByIndex.get("idx_transactions_link_created"))
                .as("attempts of one link, newest first (link_id, created_at desc)")
                .containsExactly("link_id", "created_at");
        assertThat(columnsByIndex.get("idx_transactions_status_created"))
                .as("reconciliation sweep index (status, created_at)")
                .containsExactly("status", "created_at");

        assertThat(orderingByIndex.get("idx_transactions_link_created").get(1))
                .as("created_at must be descending in the link index")
                .isEqualTo("D");
        assertThat(orderingByIndex.get("idx_transactions_status_created").get(1))
                .as("created_at must be ascending in the reconciliation index — the sweep drains oldest first")
                .isEqualTo("A");
    }
}
