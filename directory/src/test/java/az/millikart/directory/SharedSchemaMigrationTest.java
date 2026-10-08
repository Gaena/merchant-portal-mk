package az.millikart.directory;

import az.millikart.common.testing.PostgresTestContainer;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

// P1-2: общий объект вправе создать любой из владеющих им сервисов — terminals.status (P2-8) и
// audit_logs (P2-14). Иначе стартовавший первым построит таблицу без него и либо не пройдёт свой
// ddl-auto: validate, либо будет писать в несуществующую таблицу. Только здесь changelog-и разных
// сервисов встречаются.
//
// Идёт на **настоящей PostgreSQL** в контейнере, и это здесь не формальность: тест проверяет
// идемпотентность миграций, а «повторный ADD COLUMN падает» — свойство PostgreSQL, не H2. На
// эмуляции он проверял бы, что предусловия не мешают, но не что они спасают.
//
// Каждому методу — своя схема в общей базе: миграции должны видеть пустоту, как при первом
// развёртывании, а поднимать контейнер на метод значило бы платить за это минутами.
@DisplayName("shared tables survive any service starting first (P2-8, P2-14)")
public class SharedSchemaMigrationTest {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    private Connection keepAlive;
    private String url;
    private String schema;

    @BeforeEach
    void openDatabase(TestInfo testInfo) throws Exception {
        PostgreSQLContainer<?> container = PostgresTestContainer.instance();
        // Имя схемы из имени метода: в отчёте сразу видно, чья схема осталась, если тест упал.
        schema = ("s_" + testInfo.getTestMethod().orElseThrow().getName()).toLowerCase(Locale.ROOT);
        if (schema.length() > 60) {
            schema = schema.substring(0, 60);
        }
        try (Connection admin = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            statement.execute("CREATE SCHEMA " + schema);
        }

        url = container.getJdbcUrl() + "&currentSchema=" + schema;
        keepAlive = DriverManager.getConnection(url, container.getUsername(), container.getPassword());
    }

    @AfterEach
    void dropDatabase() throws Exception {
        keepAlive.close();
        PostgreSQLContainer<?> container = PostgresTestContainer.instance();
        try (Connection admin = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    // pbl создаёт terminals, companies и audit_logs сам; directory, пришедший вторым, их дополняет:
    // колонки аудита у companies, уникальный логин (Р-93), индексы журнала (P2-14).
    @Test
    @DisplayName("pbl starts first on an empty database, directory and auth follow")
    void pblFirst_thenDirectory_bothMigrate() throws Exception {
        assertDoesNotThrow(this::runPblChangelog);
        assertTrue(columnExists("terminals", "status"), "pbl must add status to the table it created");
        assertTrue(tableExists("audit_logs"), "pbl must create audit_logs when nobody else has");
        assertTrue(columnExists("audit_logs", "outcome"));
        assertTrue(columnExists("companies", "provider_login"));
        assertTrue(columnExists("companies", "provider_password"));
        assertFalse(columnExists("terminals", "password"), "the terminal password is gone");

        assertDoesNotThrow(this::runDirectoryChangelog,
                "directory must migrate onto the tables pbl created");
        assertDoesNotThrow(this::runAuthChangelog);

        assertTrue(columnExists("terminals", "status"));
        assertTrue(columnExists("companies", "created_by"), "directory completes the table pbl created");
        assertTrue(indexExists("companies", "ux_companies_provider_login"));
        assertAuditIndexesAreComplete();
    }

    // Колонки и индексы добавляются один раз, кто бы ни успел первым: повторный ADD COLUMN на PostgreSQL
    // падает, и только precondition превращает его в пропуск. Креды компании pbl находит на месте (Р-93).
    @Test
    @DisplayName("directory starts first, pbl follows, both run again: nothing is added twice")
    void directoryFirst_thenPbl_bothMigrate() throws Exception {
        assertDoesNotThrow(this::runDirectoryChangelog);
        assertTrue(columnExists("terminals", "status"), "directory must add status to the table it created");
        assertTrue(indexExists("companies", "ux_companies_provider_login"));
        assertFalse(columnExists("terminals", "password"));
        assertAuditIndexesAreComplete();

        assertDoesNotThrow(this::runPblChangelog,
                "pbl must migrate onto the tables directory created");
        assertTrue(tableExists("payment_links"), "pbl's own tables must still be created");
        assertTrue(columnExists("companies", "provider_password"));

        assertDoesNotThrow(this::runPblChangelog);
        assertDoesNotThrow(this::runDirectoryChangelog);
        assertTrue(statusColumnIsSingle(), "status must exist exactly once on terminals");
    }

    // Дамп восстановлен без DATABASECHANGELOG: Liquibase не знает ничего, а на месте уже всё. Оба
    // changeset-а обязаны уйти в ветку «уже существует», а не добавлять имеющуюся колонку.
    @Test
    @DisplayName("database restored without DATABASECHANGELOG: the status changesets skip cleanly")
    void migrations_onDatabaseWithoutChangelogTable_skipCleanly() throws Exception {
        runDirectoryChangelog();
        runPblChangelog();

        execute("DROP TABLE databasechangelog");

        assertDoesNotThrow(this::runDirectoryChangelog);
        assertDoesNotThrow(this::runPblChangelog);
        assertTrue(columnExists("terminals", "status"));
    }

    // --- P2-14: audit_logs создаёт тот пишущий сервис, который стартовал первым ---

    // Журнал пишут все три сервиса, значит создать таблицу должен уметь каждый (P2-14). auth
    // создаёт её сразу в том виде, который directory собрал бы двумя changelog-ами, и собственные
    // changeset-ы directory находят все колонки и индексы на месте и отмечаются выполненными —
    // ради этого 004 и разбит на один changeset на объект.
    @Test
    @DisplayName("auth starts first on an empty database, directory follows: one audit_logs")
    void authFirst_thenDirectory_shareOneAuditTable() throws Exception {
        assertDoesNotThrow(this::runAuthChangelog);
        assertTrue(tableExists("audit_logs"), "auth must create audit_logs when nobody else has");
        assertTrue(columnExists("audit_logs", "client_ip"), "and in its final shape, not the 2026-07 one");
        assertTrue(columnExists("audit_logs", "outcome"));
        assertTrue(columnExists("audit_logs", "trace_id"), "auth adds the trace id like the other two creators");
        assertTrue(tableExists("audit_chain") && tableExists("audit_chain_head"), "auth creates the audit chain too (Р-138)");

        assertDoesNotThrow(this::runDirectoryChangelog,
                "directory must migrate onto an audit_logs table auth created");

        assertAuditIndexesAreComplete();
        assertTrue(tableExists("companies"), "directory's own tables must still be created");
    }

    @Test
    @DisplayName("directory first, then auth and pbl: nobody tries to create audit_logs twice")
    void directoryFirst_thenTheWriters_skipTheTable() throws Exception {
        runDirectoryChangelog();

        assertDoesNotThrow(this::runAuthChangelog);
        assertDoesNotThrow(this::runPblChangelog);

        assertAuditIndexesAreComplete();
    }

    // Обновление живой базы: таблица осталась с времён до P2-8, а колонки в ней нет.
    @Test
    @DisplayName("existing terminals table without status: the column is added by whoever runs")
    void existingTerminalsTable_getsTheColumn() throws Exception {
        execute("""
                CREATE TABLE terminals (
                    id integer NOT NULL,
                    name varchar(255) NOT NULL,
                    login varchar(255) NOT NULL,
                    password varchar(255) NOT NULL,
                    company_id varchar(255),
                    CONSTRAINT pk_terminals PRIMARY KEY (id)
                )""");
        execute("INSERT INTO terminals (id, name, login, password, company_id) "
                + "VALUES (1, 'Legacy', 'l', 'p', 'comp-01')");

        runPblChangelog();

        assertTrue(columnExists("terminals", "status"));
        // Строки старше колонки остаются рабочими: их заполняет значение по умолчанию.
        try (Statement statement = keepAlive.createStatement();
             ResultSet rs = statement.executeQuery("SELECT status FROM terminals WHERE id = 1")) {
            assertTrue(rs.next());
            org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", rs.getString(1),
                    "an existing terminal must come out of the migration usable");
        }
    }

    // Р-81: номер терминала выдаёт последовательность. На живой базе терминалы уже есть, и она
    // обязана продолжить с наибольшего номера — иначе первое же заведение упадёт на занятом ключе.
    @Test
    @DisplayName("existing terminals: numbering continues after the largest id")
    void existingTerminals_numberingContinuesAfterTheLargestId() throws Exception {
        execute("""
                CREATE TABLE terminals (
                    id integer NOT NULL,
                    name varchar(255) NOT NULL,
                    login varchar(255) NOT NULL,
                    password varchar(255) NOT NULL,
                    company_id varchar(255),
                    CONSTRAINT pk_terminals PRIMARY KEY (id)
                )""");
        execute("INSERT INTO terminals (id, name, login, password) VALUES (3, 'A', 'a', 'p'), (41, 'B', 'b', 'p')");

        runDirectoryChangelog();

        try (Statement statement = keepAlive.createStatement();
             ResultSet rs = statement.executeQuery("SELECT nextval('terminals_id_seq')")) {
            assertTrue(rs.next());
            org.junit.jupiter.api.Assertions.assertEquals(42L, rs.getLong(1));
        }
        // Запись мимо сервиса тоже получает номер из той же последовательности. Пароля у терминала после
        // миграции 008 нет (Р-93).
        execute("INSERT INTO terminals (name, login) VALUES ('C', 'c')");
        try (Statement statement = keepAlive.createStatement();
             ResultSet rs = statement.executeQuery("SELECT id FROM terminals WHERE name = 'C'")) {
            assertTrue(rs.next());
            org.junit.jupiter.api.Assertions.assertEquals(43, rs.getInt(1));
        }
    }

    // --- ecom: свои слепки и колонки сверки в общей terminals ---

    // ecom стартует после directory или pbl (AGENTS §4): terminals он дополняет, но не создаёт.
    @Test
    @DisplayName("directory first, then ecom: the snapshots are created, the terminal columns are found in place")
    void directoryFirst_thenEcom_bothMigrate() throws Exception {
        assertDoesNotThrow(this::runDirectoryChangelog);
        assertDoesNotThrow(this::runEcomChangelog);

        assertTrue(columnExists("provider_terminals", "terminal_rid"));
        assertTrue(tableExists("provider_logins"));
        assertTrue(indexExists("terminals", "uk_terminals_merchant_rid"));

        assertDoesNotThrow(this::runEcomChangelog, "a second ecom run must change nothing");
        assertDoesNotThrow(this::runDirectoryChangelog, "directory after ecom must change nothing");
    }

    // pbl создаёт terminals сам; колонки сверки добавляет тот из ecom и directory, кто пришёл первым.
    @Test
    @DisplayName("pbl first, then ecom, then directory and auth: the reconciliation columns are added once")
    void pblFirst_thenEcom_thenDirectory_allMigrate() throws Exception {
        assertDoesNotThrow(this::runPblChangelog);
        assertDoesNotThrow(this::runEcomChangelog);
        assertTrue(columnExists("terminals", "status_source"));
        assertTrue(columnExists("terminals", "merchant_rid"));

        assertDoesNotThrow(this::runDirectoryChangelog);
        assertDoesNotThrow(this::runAuthChangelog);
        assertTrue(indexExists("terminals", "uk_terminals_merchant_rid"));
        assertTrue(tableExists("audit_logs"));
    }

    // --- вспомогательное ---

    private void runDirectoryChangelog() throws Exception {
        runChangelog("directory/src/main/resources");
    }

    private void runPblChangelog() throws Exception {
        runChangelog("pbl/src/main/resources");
    }

    private void runAuthChangelog() throws Exception {
        runChangelog("auth/src/main/resources");
    }

    private void runEcomChangelog() throws Exception {
        runChangelog("ecom/src/main/resources");
    }

    private void runChangelog(String moduleResources) throws Exception {
        Path resources = SharedDatabaseSchema.repositoryRoot().resolve(moduleResources);
        if (!Files.isDirectory(resources)) {
            throw new IllegalStateException("changelog resources not found at " + resources);
        }
        PostgreSQLContainer<?> container = PostgresTestContainer.instance();
        try (Connection connection = DriverManager.getConnection(
                url, container.getUsername(), container.getPassword())) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (DirectoryResourceAccessor accessor = new DirectoryResourceAccessor(resources);
                 Liquibase liquibase = new Liquibase(CHANGELOG, accessor, database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
    }

    private void execute(String sql) throws Exception {
        try (Statement statement = keepAlive.createStatement()) {
            statement.execute(sql);
        }
    }

    private boolean tableExists(String table) throws Exception {
        try (ResultSet rs = keepAlive.getMetaData().getTables(null, schema, upper(table), null)) {
            return rs.next();
        }
    }

    private boolean columnExists(String table, String column) throws Exception {
        try (ResultSet rs = keepAlive.getMetaData().getColumns(null, schema, upper(table), upper(column))) {
            return rs.next();
        }
    }

    private boolean indexExists(String table, String indexName) throws Exception {
        if (!tableExists(table)) {
            return false;
        }
        try (ResultSet rs = keepAlive.getMetaData().getIndexInfo(null, schema, upper(table), false, false)) {
            while (rs.next()) {
                if (indexName.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    // Индексы журнала — по метаданным схемы, а не по DATABASECHANGELOG: changeset, тихо пропущенный
    // предусловием, отмечен выполненным и оставил бы таблицу без индекса. Кто бы ни создал audit_logs,
    // колонки и порядок те же: по компании и по времени — от новых к старым.
    private void assertAuditIndexesAreComplete() throws Exception {
        Map<String, List<String>> columns = new HashMap<>();
        Map<String, List<String>> ordering = new HashMap<>();
        try (ResultSet rs = keepAlive.getMetaData().getIndexInfo(null, schema, upper("audit_logs"), false, false)) {
            while (rs.next()) {
                String index = rs.getString("INDEX_NAME");
                String column = rs.getString("COLUMN_NAME");
                if (index == null || column == null) {
                    continue;
                }
                String key = index.toLowerCase(Locale.ROOT);
                columns.computeIfAbsent(key, k -> new ArrayList<>()).add(column.toLowerCase(Locale.ROOT));
                ordering.computeIfAbsent(key, k -> new ArrayList<>()).add(rs.getString("ASC_OR_DESC"));
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(List.of("company_id", "created_at"),
                columns.get("idx_audit_logs_company_created"));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("entity_type", "entity_id"), columns.get("idx_audit_logs_entity"));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("created_at"), columns.get("idx_audit_logs_created"));
        org.junit.jupiter.api.Assertions.assertEquals("D", ordering.get("idx_audit_logs_company_created").get(1),
                "created_at must be descending in the company index");
        org.junit.jupiter.api.Assertions.assertEquals("D", ordering.get("idx_audit_logs_created").getFirst(),
                "created_at must be descending in the administrator index");
    }

    private boolean statusColumnIsSingle() throws Exception {
        int count = 0;
        try (ResultSet rs = keepAlive.getMetaData().getColumns(null, schema, upper("terminals"), upper("status"))) {
            while (rs.next()) {
                count++;
            }
        }
        return count == 1;
    }

    // PostgreSQL складывает некавыченные идентификаторы в нижнем регистре, а JDBC-метаданные
    // сравнивают их буквально. С H2 здесь было ровно наоборот — верхний регистр.
    private static String upper(String identifier) {
        return identifier.toLowerCase(Locale.ROOT);
    }
}
