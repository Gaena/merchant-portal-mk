package az.millikart.common.testing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Настоящая PostgreSQL для тестов, которым H2 врёт.
 *
 * Зачем вообще: тесты этого проекта по умолчанию идут на H2 в режиме совместимости, и это
 * эмуляция, а не PostgreSQL. Расходятся они там, где нам важнее всего:
 * частичные и условные индексы, тип jsonb, семантика блокировок под `SELECT ... FOR UPDATE`
 * и сами миграции, которые до выката иначе не исполняются ни разу на той СУБД, что в проде.
 *
 * Контейнер **один на всю JVM**: статическое поле, поднимается при первом обращении и живёт до
 * конца сборки. Контейнер на класс превратил бы полминуты тестов в минуты, а Spring всё равно
 * кеширует контексты, так что второй раз его никто не ждёт.
 *
 * `withReuse(true)` работает только там, где разработчик включил переиспользование у себя
 * (`testcontainers.reuse.enable=true` в `~/.testcontainers.properties`). В CI флаг игнорируется,
 * и контейнер живёт ровно одну сборку — то, что нужно.
 *
 * Версия образа прибита намеренно и должна совпадать с продовой: смысл этих тестов в том, чтобы
 * проверять ту же СУБД, а «latest» однажды поедет и проверит другую.
 */
/*
 * `@TestConfiguration`, а не `@Configuration`, и это не косметика. Сервисы сканируют пакет
 * `az.millikart` целиком, куда попадает и этот класс, — обычная `@Configuration` подхватилась бы
 * автоматически **во всех** тестах сразу, и весь набор молча уехал бы с H2 на контейнер, деля
 * одну базу между классами. `@TestConfiguration` из сканирования исключается, и конфигурация
 * достаётся только тем тестам, которые попросили её явным `@Import`.
 */
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

    /** Готовый контейнер для тестов, которым нужен доступ к нему напрямую, мимо Spring. */
    public static PostgreSQLContainer<?> instance() {
        return CONTAINER;
    }

    /**
     * `@ServiceConnection` сам подставляет адрес, логин и пароль в `spring.datasource`, поэтому
     * тестовому классу достаточно импортировать эту конфигурацию и ничего не переопределять.
     */
    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return CONTAINER;
    }
}
