package az.millikart.ecom.repository;

import az.millikart.ecom.config.TxpgDataSourceConfig;
import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.service.ProviderTerminalSource;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Список терминалов провайдера из схемы шлюза.
 *
 * ВНИМАНИЕ: запрос ниже — **предварительный**. Он собран по разбору стенда (`txpg_query_review.md`,
 * §«Диагностика»: `select rid, title from txpg.merchant`) и ждёт замены на SQL, который пришлёт
 * провайдер. Достоверно из разбора известны только `rid` и `title`; где лежит логин терминала и
 * чем отличается активный от снятого с обслуживания — предстоит уточнить.
 *
 * Поэтому здесь нет ни одной подстановки «по смыслу»: чего в ответе нет, то приезжает пустым и
 * дальше по цепочке видно как пустое. Выдуманный логин не проще починить, чем отсутствующий, —
 * он молча не сойдётся с тем, чем терминал ходит в шлюз, и выяснится это на первом платеже.
 *
 * Всё, что вокруг этого класса — правила гашения, подтверждение пропаданием, сверка с нашими
 * терминалами — от формы запроса не зависит и при его замене переписываться не должно.
 */
@Repository
public class TxpgProviderTerminalSource implements ProviderTerminalSource {

    private final NamedParameterJdbcTemplate jdbc;
    private final TxpgProperties properties;

    public TxpgProviderTerminalSource(@Qualifier(TxpgDataSourceConfig.TXPG_JDBC) NamedParameterJdbcTemplate jdbc,
                                      TxpgProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Override
    public List<ProviderTerminalRow> fetchActive() {
        String sql = """
                select m.rid   rid,
                       m.title title,
                       m.login login
                  from %s.merchant m
                 order by m.title
                """.formatted(properties.getSchema());

        // Исключение наружу не гасится намеренно: вызывающий обязан отличить «провайдер сказал,
        // что терминалов нет» от «спросить не удалось», и сделать это можно только так.
        return jdbc.query(sql, new MapSqlParameterSource(), (rs, rowNum) -> new ProviderTerminalRow(
                rs.getString("rid"),
                rs.getString("title"),
                rs.getString("login")
        ));
    }
}
