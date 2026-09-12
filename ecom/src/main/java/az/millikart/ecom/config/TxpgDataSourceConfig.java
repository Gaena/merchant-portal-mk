package az.millikart.ecom.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Два источника данных в одном процессе, и их нельзя путать.
 *
 * `spring.datasource` — наша PostgreSQL: привязки мерчантов, журнал аудита, Liquibase. Помечена
 * `@Primary`, поэтому JPA, Liquibase и всё, что просит `DataSource` без уточнения, достаётся ей.
 * Иначе Spring выбрал бы любой из двух, и миграции однажды уехали бы в чужую базу.
 *
 * `ecom.txpg.datasource` — схема шлюза. Отдельный пул намеренно маленький: отчётный запрос по
 * операционной базе конкурирует с авторизациями, и ограничение пула — единственное, чем мы можем
 * ограничить свой вред, пока провайдер не выдаст читающую реплику.
 *
 * Транзакционного менеджера у второго источника нет и не будет: сюда ходят голым
 * `NamedParameterJdbcTemplate`, соединение read-only, и ни одна наша запись в чужую базу
 * невозможна просто потому, что писать нечем.
 */
@Configuration
public class TxpgDataSourceConfig {

    public static final String TXPG_JDBC = "txpgJdbcTemplate";

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties portalDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties portalDataSourceProperties) {
        return portalDataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @ConfigurationProperties("ecom.txpg.datasource")
    public DataSourceProperties txpgDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Пул к шлюзу. `read-only` ставится на соединения пулом, а не надеждой на то, что в коде
     * не появится INSERT: права учётки — второй рубеж, а не первый.
     */
    @Bean("txpgDataSource")
    @ConfigurationProperties("ecom.txpg.datasource.hikari")
    public DataSource txpgDataSource(@Qualifier("txpgDataSourceProperties") DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setReadOnly(true);
        dataSource.setPoolName("txpg-read");
        return dataSource;
    }

    @Bean(TXPG_JDBC)
    public NamedParameterJdbcTemplate txpgJdbcTemplate(@Qualifier("txpgDataSource") DataSource txpgDataSource,
                                                       TxpgProperties properties) {
        JdbcTemplate template = new JdbcTemplate(txpgDataSource);
        // Потолок на время запроса — наш собственный предохранитель. Выписка за квартал по
        // крупному мерчанту не должна держать соединение к боевому шлюзу неограниченно долго;
        // у провайдера свой statement timeout, но полагаться на чужую настройку нельзя.
        template.setQueryTimeout((int) properties.getQueryTimeout().toSeconds());
        template.setFetchSize(properties.getFetchSize());
        return new NamedParameterJdbcTemplate(template);
    }
}
