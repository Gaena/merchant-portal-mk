package az.millikart.directory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;

// Прогоняет changelog чужого сервиса на базе, которой владеют тесты этого модуля: общая база есть
// только в проде, а каждый модуль поднимает свою H2, и чужих таблиц, которые directory читает и пишет
// (payment_links pbl, слепки provider_* ecom), в его тестах просто нет. Changelog читается по пути из
// исходников сервиса, а не с classpath: переименованная там колонка должна ломать тесты, а не прод.
final class SharedDatabaseSchema {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    private SharedDatabaseSchema() {
    }

    // Создаёт payment_links, transactions и остальную схему pbl.
    static void applyPblChangelog(Connection connection) throws Exception {
        applyChangelog(connection, "pbl");
    }

    // Создаёт слепки ecom — provider_terminals и provider_logins. Идёт после directory, как в проде (AGENTS §4):
    // terminals ecom дополняет, но не создаёт.
    static void applyEcomChangelog(Connection connection) throws Exception {
        applyChangelog(connection, "ecom");
    }

    private static void applyChangelog(Connection connection, String module) throws Exception {
        Path resources = repositoryRoot().resolve(module + "/src/main/resources");
        if (!Files.isDirectory(resources)) {
            throw new IllegalStateException(module + " resources not found at " + resources
                    + " — this test reads " + module + "'s changelog from the source tree");
        }
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        try (DirectoryResourceAccessor accessor = new DirectoryResourceAccessor(resources);
             Liquibase liquibase = new Liquibase(CHANGELOG, accessor, database)) {
            liquibase.update(new Contexts(), new LabelExpression());
        }
    }

    // Поднимается от рабочего каталога (под Gradle это каталог модуля) до того, где лежат исходники
    // всех модулей. Маркером служит сама раскладка модулей, а не settings.gradle: у каждого модуля
    // остался свой settings.gradle с времён отдельного проекта, и он совпадёт уровнем раньше.
    static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("pbl/src/main/resources"))
                    && Files.isDirectory(current.resolve("directory/src/main/resources"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root not found above " + Path.of("").toAbsolutePath()
                + " — expected a directory containing both pbl/ and directory/ module sources");
    }
}
