package az.millikart.ecom.repository;

import az.millikart.ecom.config.TxpgDataSourceConfig;
import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.service.ProviderLoginSource;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// Логины мультимерчантов провайдера — по его SQL от 24.09.2026, урезанному до нужного проверке (Р-94):
// login → login2merchant → merchant. Терминал, psp и pmo у MultiMerchantSys пусты и не читаются.
// left join и статусы без фильтра — намеренно: отказ обязан отличать «логина нет» от «выключен».
@Repository
public class TxpgProviderLoginSource implements ProviderLoginSource {

    static final String MULTI_MERCHANT_OWNER = "MultiMerchantSys";

    private final NamedParameterJdbcTemplate jdbc;
    private final TxpgProperties properties;

    public TxpgProviderLoginSource(@Qualifier(TxpgDataSourceConfig.TXPG_JDBC) NamedParameterJdbcTemplate jdbc,
                                   TxpgProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Override
    public List<ProviderLoginRow> fetchMultiMerchantLogins() {
        String sql = """
                select l.login   login,
                       l.status  login_status,
                       lm.status link_status,
                       m.rid     merchant_rid,
                       m.title   merchant_title
                  from %1$s.login l
                  left join %1$s.login2merchant lm on lm.loginid = l.id
                  left join %1$s.merchant m on m.id = lm.merchantid
                 where l.ownerkind = :ownerKind
                 order by l.login, m.rid
                """.formatted(properties.getSchema());

        return jdbc.query(sql, new MapSqlParameterSource("ownerKind", MULTI_MERCHANT_OWNER), (rs, rowNum) ->
                new ProviderLoginRow(
                        rs.getString("login"),
                        rs.getString("login_status"),
                        rs.getString("link_status"),
                        rs.getString("merchant_rid"),
                        rs.getString("merchant_title")));
    }
}
