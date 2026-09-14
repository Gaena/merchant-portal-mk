package az.millikart.ecom.repository;

import az.millikart.ecom.config.TxpgDataSourceConfig;
import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.service.ProviderTerminalSource;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// Справочник терминалов провайдера — по SQL провайдера от 14.09.2026 (Р-79): e-commerce терминалы
// процессинга 70 (TID на PBY), у которых активны и логин, и терминал. Выключенный у провайдера
// пропадает из выгрузки, и через три опроса гаснет наш терминал со ссылками (Р-66).
@Repository
public class TxpgProviderTerminalSource implements ProviderTerminalSource {

    // Процессинг и префикс TID из запроса провайдера: терминалы других PMO и TID порталу не принадлежат.
    static final String PMO_RID = "70";
    static final String TID_PATTERN = "PBY%";

    private final NamedParameterJdbcTemplate jdbc;
    private final TxpgProperties properties;

    public TxpgProviderTerminalSource(@Qualifier(TxpgDataSourceConfig.TXPG_JDBC) NamedParameterJdbcTemplate jdbc,
                                      TxpgProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    // Ключ — merchant.rid: по нему выписка находит заказы, и у провайдера один терминал — один мерчант
    // (Р-67). Название — мерчанта, как в выписке. Исключение наружу не гасится: вызывающий обязан
    // отличить «терминалов нет» от «спросить не удалось».
    @Override
    public List<ProviderTerminalRow> fetchActive() {
        String sql = """
                select m.rid   rid,
                       m.title title,
                       l.login login
                  from %1$s.login l
                  join %1$s.terminal    t  on t.id = l.terminalid
                  join %1$s.terminalpmo tp on tp.terminalid = t.id
                  join %1$s.merchant    m  on m.id = t.merchantid
                 where tp.pmorid = :pmo_rid
                   and tp.tid like :tid_pattern
                   and l.status = 'Active'
                   and t.status = 'Active'
                 order by m.rid, l.login
                """.formatted(properties.getSchema());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("pmo_rid", PMO_RID)
                .addValue("tid_pattern", TID_PATTERN);

        return jdbc.query(sql, params, (rs, rowNum) -> new ProviderTerminalRow(
                rs.getString("rid"),
                rs.getString("title"),
                rs.getString("login")
        ));
    }
}
