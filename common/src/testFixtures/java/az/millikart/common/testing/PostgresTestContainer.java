package az.millikart.common.testing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// Настоящая PostgreSQL для тестов, где H2 расходится с ней: индексы, jsonb, блокировки, миграции (Р-68).
// Один контейнер на JVM. Образ прибит и совпадает с продом (Р-73): «latest» однажды проверит не ту СУБД.
// @TestConfiguration, а не @Configuration: сервисы сканируют az.millikart целиком, и обычная
// конфигурация молча перевела бы на контейнер все тесты разом.
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainer {

    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:16-alpine");

    private static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("mp")
            .withUsername("mp")
            .withPassword("mp")
            .withReuse(true);

    static {
        CONTAINER.start();
    }

    // Для тестов без Spring.
    public static PostgreSQLContainer<?> instance() {
        return CONTAINER;
    }

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return CONTAINER;
    }
}
