package az.millikart.auth;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
import org.springframework.jdbc.core.JdbcTemplate;

// Терминалы сотрудников (Р-131): auth проверяет компанию терминала в таблице terminals, которую создаёт
// directory. Тесты получают её настоящим changelog directory поверх уже поднятой базы auth — как в проде,
// где directory стартует рядом, — а не рукописной копией DDL (§11). Классу, заводящему терминалы, —
// clean() в @AfterEach: строки в чужой таблице общей базы убирает тот, кто их оставил.
final class TerminalFixture {

    // Тесты идут из каталога модуля auth, directory — соседний модуль.
    private static final Path DIRECTORY_RESOURCES = Path.of("..", "directory", "src", "main", "resources");

    private static final Set<DataSource> MIGRATED = ConcurrentHashMap.newKeySet();

    private TerminalFixture() {
    }

    static void ensureSchema(DataSource dataSource) {
        if (MIGRATED.contains(dataSource)) {
            return;
        }
        if (!Files.isDirectory(DIRECTORY_RESOURCES)) {
            throw new IllegalStateException("directory changelog not found at " + DIRECTORY_RESOURCES.toAbsolutePath());
        }
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (DirectoryResourceAccessor accessor = new DirectoryResourceAccessor(DIRECTORY_RESOURCES)) {
                Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.xml", accessor, database);
                liquibase.update(new Contexts(), new LabelExpression());
            }
        } catch (Exception e) {
            throw new IllegalStateException("the directory changelog did not apply to the auth test database", e);
        }
        MIGRATED.add(dataSource);
    }

    // Терминал компании с постоянным номером: повторный вызов возвращает тот же, а не заводит второй.
    static int terminalOf(JdbcTemplate jdbc, String companyId) {
        int id = 1000 + Math.floorMod(companyId.hashCode(), 100_000);
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM terminals WHERE id = ?", Integer.class, id);
        if (existing == null || existing == 0) {
            jdbc.update("INSERT INTO terminals (id, name, login, company_id, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                    id, "Terminal of " + companyId, "TerminalSys/" + companyId, companyId);
        }
        return id;
    }

    static int extraTerminalOf(JdbcTemplate jdbc, String companyId, int offset) {
        int id = terminalOf(jdbc, companyId) + offset;
        jdbc.update("INSERT INTO terminals (id, name, login, company_id, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                id, "Terminal " + offset + " of " + companyId, "TerminalSys/" + companyId + "-" + offset, companyId);
        return id;
    }

    static void clean(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM user_terminals");
        jdbc.update("DELETE FROM terminals");
    }
}
