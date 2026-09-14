package az.millikart.ecom.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.service.ProviderTerminalSource.ProviderTerminalRow;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

// Запрос справочника терминалов провайдера (Р-79). Сам SQL здесь не исполняется — их схему не поднять
// в контейнере. Проверяется то, чья цена — выключенные терминалы и остановленные ссылки: фильтр по
// статусу, процессингу и PBY, и что ключом справочника остаётся мерчант, по которому идёт выписка.
@SuppressWarnings("unchecked")
class TxpgProviderTerminalSourceTest {

    @Test
    void onlyActivePbyTerminalsOfTheProcessingAreFetched() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        new TxpgProviderTerminalSource(jdbc, new TxpgProperties()).fetchActive();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        String text = sql.getValue().replaceAll("\\s+", " ");
        for (String fragment : List.of(
                "from TXPG.login l",
                "join TXPG.terminal t on t.id = l.terminalid",
                "join TXPG.terminalpmo tp on tp.terminalid = t.id",
                "join TXPG.merchant m on m.id = t.merchantid",
                "where tp.pmorid = :pmo_rid",
                "and tp.tid like :tid_pattern",
                "and l.status = 'Active'",
                "and t.status = 'Active'")) {
            Assertions.assertTrue(text.contains(fragment), fragment + " in " + text);
        }
        MapSqlParameterSource values = (MapSqlParameterSource) params.getValue();
        Assertions.assertEquals("70", values.getValue("pmo_rid"));
        Assertions.assertEquals("PBY%", values.getValue("tid_pattern"));
    }

    // Ключ справочника — merchant.rid: по нему выписка находит заказы. Возьми сюда terminal.rid — и
    // терминал, заведённый из справочника, перестанет видеть свои платежи.
    @Test
    void theRowIsKeyedByTheMerchant() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        new TxpgProviderTerminalSource(jdbc, new TxpgProperties()).fetchActive();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RowMapper<ProviderTerminalRow>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), mapper.capture());

        String text = sql.getValue().replaceAll("\\s+", " ");
        Assertions.assertTrue(text.contains("select m.rid rid, m.title title, l.login login"), text);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("rid")).thenReturn("223456789054322");
        when(rs.getString("title")).thenReturn("BazarStore eCommerce");
        when(rs.getString("login")).thenReturn("BS00002");
        Assertions.assertEquals(new ProviderTerminalRow("223456789054322", "BazarStore eCommerce", "BS00002"),
                mapper.getValue().mapRow(rs, 0));
    }
}
