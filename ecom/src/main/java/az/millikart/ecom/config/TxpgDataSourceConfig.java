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

// Два источника данных. Наша PostgreSQL помечена @Primary: без этого Spring выбрал бы любой из двух,
// и миграции однажды уехали бы в чужую базу. Пул к шлюзу маленький — отчёты конкурируют с
// авторизациями — и без транзакционного менеджера: писать в чужую базу нечем (AGENTS.md §10).
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

    // read-only ставит пул, а не надежда, что в коде не появится INSERT; права учётки — второй рубеж.
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
