package az.millikart.ecom.repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

// Таблицы чужих модулей (transactions у pbl) бывают ещё не созданы — как SharedTables в directory. Проверка —
// в соединении текущей транзакции, а не второе соединение из пула (TABLE-CHECK-POOL); ответ не кэшируется.
@Component
public class SharedTables {

    private static final Logger log = LoggerFactory.getLogger(SharedTables.class);

    private final DataSource dataSource;

    public SharedTables(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // true — таблицы точно нет. Проверить не удалось — false: запрос сам упадёт, если база правда недоступна.
    public boolean missing(String table) {
        Connection connection;
        try {
            connection = DataSourceUtils.getConnection(dataSource);
        } catch (CannotGetJdbcConnectionException e) {
            log.warn("Could not determine whether {} exists; attempting the statement anyway", table, e);
            return false;
        }
        try {
            return !tableExists(connection, table) && !tableExists(connection, table.toUpperCase(Locale.ROOT));
        } catch (SQLException e) {
            log.warn("Could not determine whether {} exists; attempting the statement anyway", table, e);
            return false;
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static boolean tableExists(Connection connection, String name) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, name, null)) {
            return tables.next();
        }
    }
}
