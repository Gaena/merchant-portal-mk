package az.millikart.common.testing;

import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
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

    // Без withReuse: переиспользованный контейнер делил бы одну базу между модулями и прогонами, и
    // параллельные модули писали бы в одни таблицы. Гасит его Ryuk, когда JVM завершается.
    private static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("mp")
            .withUsername("mp")
            .withPassword("mp");

    static {
        CONTAINER.start();
    }

    // Для тестов без Spring.
    public static PostgreSQLContainer<?> instance() {
        return CONTAINER;
    }

    // Контексту — только адрес, а не сам контейнер: контейнер-бин Spring Boot останавливает при закрытии
    // контекста, и закрытие одного контекста погасило бы базу для остальных в этой JVM.
    @Bean
    public JdbcConnectionDetails postgresConnectionDetails() {
        return new JdbcConnectionDetails() {
            @Override
            public String getUsername() {
                return CONTAINER.getUsername();
            }

            @Override
            public String getPassword() {
                return CONTAINER.getPassword();
            }

            @Override
            public String getJdbcUrl() {
                return CONTAINER.getJdbcUrl();
            }
        };
    }
}
