package az.millikart.directory;

import static org.assertj.core.api.Assertions.assertThat;

import az.millikart.directory.repository.SharedTables;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// TABLE-CHECK-POOL: проверка чужой таблицы брала своё соединение из пула, хотя вызывающий уже держал
// соединение транзакции. При занятом пуле она ждала connectionTimeout и отвечала «неизвестно» — блокировка
// терминала при десяти одновременных правках висела секундами. Пул здесь из одного соединения: второе
// взять негде, и правильный ответ возможен только из соединения транзакции. Не спринговый, своя H2.
class SharedTablesTest {

    private HikariDataSource pool;
    private TransactionTemplate transaction;
    private SharedTables sharedTables;

    @BeforeEach
    void openPoolOfOne() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:shared-tables-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(250);
        pool = new HikariDataSource(config);
        new JdbcTemplate(pool).execute("CREATE TABLE payment_links (id INT)");
        transaction = new TransactionTemplate(new DataSourceTransactionManager(pool));
        sharedTables = new SharedTables(pool);
    }

    @AfterEach
    void closePool() {
        pool.close();
    }

    @Test
    void insideATransaction_theCheckUsesItsConnection_andAnswersRight() {
        Boolean[] answers = transaction.execute(status -> {
            new JdbcTemplate(pool).queryForObject("SELECT COUNT(*) FROM payment_links", Integer.class);
            return new Boolean[] {sharedTables.missing("payment_links"), sharedTables.missing("provider_logins")};
        });

        assertThat(answers).containsExactly(false, true);
    }
}
