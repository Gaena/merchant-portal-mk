package az.millikart.ecom;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

// companies, terminals и audit_logs ecom не создаёт — в проде он стартует после directory (AGENTS §4).
// Тесты повторяют это настоящим changelog directory, а не рукописной копией DDL. Повторный прогон на
// той же базе ничего не делает: Liquibase помнит применённое.
public class DirectorySchemaInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    // Тесты идут из каталога модуля ecom, directory — соседний модуль.
    private static final Path DIRECTORY_RESOURCES = Path.of("..", "directory", "src", "main", "resources");

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment environment = context.getEnvironment();
        String url = environment.getRequiredProperty("spring.datasource.url");
        if (!Files.isDirectory(DIRECTORY_RESOURCES)) {
            throw new IllegalStateException("directory changelog not found at " + DIRECTORY_RESOURCES.toAbsolutePath());
        }
        try (Connection connection = DriverManager.getConnection(url,
                environment.getProperty("spring.datasource.username"),
                environment.getProperty("spring.datasource.password"))) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (DirectoryResourceAccessor accessor = new DirectoryResourceAccessor(DIRECTORY_RESOURCES);
                 Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.xml", accessor, database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        } catch (Exception e) {
            throw new IllegalStateException("the directory changelog did not apply to " + url, e);
        }
    }
}
