package az.millikart.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

// P1-2: три сервиса делят одну базу и один DATABASECHANGELOG, поэтому changelog auth обязан
// пережить запуск после того, как другой сервис создал общие таблицы, и не потерять межсервисный
// внешний ключ, если запущен раньше них. Прочие тесты поднимают модуль на своей H2 (mem:auth,
// mem:directory, mem:pbl) — сервисы там не встречаются. Liquibase гоняется вручную, без Spring.
@DisplayName("auth migrations do not depend on service start order (P1-2)")
public class MigrationOrderTest {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    // Держится открытым всю жизнь теста: in-memory база умирает с последним соединением.
    private Connection keepAlive;
    private String url;

    @BeforeEach
    void openDatabase(TestInfo testInfo) throws Exception {
        // База на тестовый метод — эти сценарии различаются именно тем, что уже существует.
        url = "jdbc:h2:mem:auth-migration-" + testInfo.getTestMethod().orElseThrow().getName()
                + ";MODE=PostgreSQL";
        keepAlive = DriverManager.getConnection(url, "sa", "");
    }

    @AfterEach
    void dropDatabase() throws Exception {
        keepAlive.close();
    }

    @Test
    @DisplayName("directory got there first: auth still migrates, users is created")
    void authMigrations_whenCompaniesAlreadyExists_succeed() throws Exception {
        // companies ровно как её создаёт directory/003-directory-schema.xml, с audit-колонками.
        execute("""
                CREATE TABLE companies (
                    id varchar(255) NOT NULL,
                    name varchar(255) NOT NULL,
                    status varchar(50) NOT NULL,
                    created_by varchar(255),
                    created_at timestamp,
                    updated_by varchar(255),
                    updated_at timestamp,
                    CONSTRAINT pk_companies PRIMARY KEY (id)
                )""");

        assertDoesNotThrow(this::runAuthChangelog, "auth must not fail on a companies table it did not create");

        assertTrue(tableExists("users"), "users must be created even though companies already existed");
        assertTrue(columnExists("companies", "updated_by"), "the existing companies table must be left alone");
        assertTrue(foreignKeyExists("users", "fk_users_company"), "fk_users_company must be created");
    }

    @Test
    @DisplayName("empty database: companies and users are both created")
    void authMigrations_onEmptyDatabase_createUsersAndCompanies() throws Exception {
        runAuthChangelog();

        assertTrue(tableExists("companies"));
        assertTrue(tableExists("users"));
        assertTrue(columnExists("users", "failed_login_attempts"), "2-auth-lockout-fields must run too");
        assertTrue(foreignKeyExists("users", "fk_users_company"));
        assertFalse(tableExists("terminals"), "auth does not own terminals and must not create it");
        // P1-12: 003-refresh-tokens.xml идёт в том же changelog и по той же конвенции.
        assertTrue(tableExists("refresh_tokens"), "3-auth-refresh-tokens must run too");
        assertTrue(indexExists("refresh_tokens", "idx_refresh_tokens_user"));
        assertTrue(indexExists("refresh_tokens", "idx_refresh_tokens_family"));
        assertTrue(foreignKeyExists("refresh_tokens", "fk_refresh_tokens_user"));
    }

    @Test
    @DisplayName("terminals appears later: fk_terminals_company is created on the next run, not lost")
    void terminalFk_isCreatedOnALaterRun_whenTerminalsAppears() throws Exception {
        // Рекомендованный порядок старта: auth первым, раньше чем pbl/directory создадут terminals.
        runAuthChangelog();
        assertFalse(tableExists("terminals"));

        // pbl/directory стартуют и создают таблицу (pbl/001-initial-schema.xml).
        execute("""
                CREATE TABLE terminals (
                    id integer NOT NULL,
                    name varchar(255) NOT NULL,
                    login varchar(255) NOT NULL,
                    password varchar(255) NOT NULL,
                    company_id varchar(255),
                    CONSTRAINT pk_terminals PRIMARY KEY (id)
                )""");

        runAuthChangelog();

        // С onFail="MARK_RAN" changeset записался бы выполненным на первом прогоне и пропущен
        // здесь, а ограничение осталось бы отсутствующим навсегда.
        assertTrue(foreignKeyExists("terminals", "fk_terminals_company"),
                "fk_terminals_company must appear once terminals exists");
    }

    @Test
    @DisplayName("running the changelog twice changes nothing and fails nothing")
    void authMigrations_runTwice_areIdempotent() throws Exception {
        runAuthChangelog();

        assertDoesNotThrow(this::runAuthChangelog);

        assertTrue(tableExists("companies"));
        assertTrue(tableExists("users"));
        assertTrue(foreignKeyExists("users", "fk_users_company"));
        assertTrue(tableExists("refresh_tokens"));
        assertTrue(foreignKeyExists("refresh_tokens", "fk_refresh_tokens_user"));
    }

    @Test
    @DisplayName("database restored without DATABASECHANGELOG: every changeset skips cleanly")
    void authMigrations_onDatabaseWithoutChangelogTable_skipEverything() throws Exception {
        runAuthChangelog();
        execute("""
                CREATE TABLE terminals (
                    id integer NOT NULL,
                    name varchar(255) NOT NULL,
                    company_id varchar(255),
                    CONSTRAINT pk_terminals PRIMARY KEY (id)
                )""");
        runAuthChangelog();
        assertTrue(foreignKeyExists("terminals", "fk_terminals_company"));

        // Дамп, восстановленный без служебной таблицы: объекты на месте, Liquibase не знает ничего.
        // Это ветка, где preconditions обязаны ответить "уже существует", и она же доказывает, что
        // foreignKeyConstraintExists работает на H2, а не только на Postgres.
        execute("DROP TABLE databasechangelog");

        assertDoesNotThrow(this::runAuthChangelog);

        assertTrue(foreignKeyExists("users", "fk_users_company"));
        assertTrue(foreignKeyExists("terminals", "fk_terminals_company"));
        assertTrue(columnExists("users", "failed_login_attempts"));
        // 003 (P1-12) обязан уйти здесь в ветку "уже существует" для таблицы, обоих индексов и FK —
        // четыре отдельных precondition, по одному на changeset.
        assertTrue(tableExists("refresh_tokens"));
        assertTrue(indexExists("refresh_tokens", "idx_refresh_tokens_user"));
        assertTrue(indexExists("refresh_tokens", "idx_refresh_tokens_family"));
        assertTrue(foreignKeyExists("refresh_tokens", "fk_refresh_tokens_user"));
    }

    // Хелперы

    private void runAuthChangelog() throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
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
        try (ResultSet rs = metaData().getTables(null, null, upper(table), null)) {
            return rs.next();
        }
    }

    private boolean columnExists(String table, String column) throws Exception {
        try (ResultSet rs = metaData().getColumns(null, null, upper(table), upper(column))) {
            return rs.next();
        }
    }

    private boolean foreignKeyExists(String table, String constraintName) throws Exception {
        if (!tableExists(table)) {
            return false;
        }
        try (ResultSet rs = metaData().getImportedKeys(null, null, upper(table))) {
            while (rs.next()) {
                if (constraintName.equalsIgnoreCase(rs.getString("FK_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean indexExists(String table, String indexName) throws Exception {
        if (!tableExists(table)) {
            return false;
        }
        try (ResultSet rs = metaData().getIndexInfo(null, null, upper(table), false, false)) {
            while (rs.next()) {
                if (indexName.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private DatabaseMetaData metaData() throws Exception {
        return keepAlive.getMetaData();
    }

    // H2 сворачивает неэкранированные идентификаторы в верхний регистр, а метаданные JDBC
    // сравнивают их буквально.
    private static String upper(String identifier) {
        return identifier.toUpperCase(Locale.ROOT);
    }
}
