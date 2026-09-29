package az.millikart.ecom.repository;

import az.millikart.ecom.config.TxpgDataSourceConfig;
import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.service.ProviderTerminalSource;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// Справочник терминалов провайдера (Р-79, Р-96): терминалы с активным логином TerminalSys и активным
// терминалом, terminalpmo — только соединение, без фильтра. Выключенный у провайдера пропадает из
// выгрузки, и через три опроса гаснет наш терминал со ссылками (Р-66).
@Repository
public class TxpgProviderTerminalSource implements ProviderTerminalSource {

    private final NamedParameterJdbcTemplate jdbc;
    private final TxpgProperties properties;

    public TxpgProviderTerminalSource(@Qualifier(TxpgDataSourceConfig.TXPG_JDBC) NamedParameterJdbcTemplate jdbc,
                                      TxpgProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    // Ключ — merchant.rid: по нему выписка находит заказы (Р-79); название — мерчанта, как в выписке.
    // Только TerminalSys: логин TerminalUser того же терминала сделал бы мерчанта неоднозначным (Р-96).
    @Override
    public List<ProviderTerminalRow> fetchActive() {
        String sql = """
                select m.rid   rid,
                       m.title title,
                       l.login login,
                       t.rid   terminal_rid
                  from %1$s.login l
                  join %1$s.terminal    t  on t.id = l.terminalid
                  join %1$s.terminalpmo tp on tp.terminalid = t.id
                  join %1$s.merchant    m  on m.id = t.merchantid
                 where l.ownerkind = 'TerminalSys'
                   and l.status = 'Active'
                   and t.status = 'Active'
                 order by m.rid, l.login
                """.formatted(properties.getSchema());

        return jdbc.query(sql, new MapSqlParameterSource() , (rs, rowNum) -> new ProviderTerminalRow(
                rs.getString("rid"),
                rs.getString("title"),
                rs.getString("login"),
                rs.getString("terminal_rid")
        ));
    }
}
