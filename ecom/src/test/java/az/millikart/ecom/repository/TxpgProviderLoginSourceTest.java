package az.millikart.ecom.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.service.ProviderLoginSource.ProviderLoginRow;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

// Запрос логинов мультимерчантов (Р-94). SQL здесь не исполняется — схему шлюза не поднять. Сторожится
// то, что ломает проверку компаний: только MultiMerchantSys, связи через login2merchant, left join —
// логин без связей виден, — и ни одной колонки сверх присланного провайдером SQL.
@SuppressWarnings("unchecked")
class TxpgProviderLoginSourceTest {

    @Test
    void onlyMultiMerchantLogins_withTheirLinks_areRead() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        new TxpgProviderLoginSource(jdbc, new TxpgProperties()).fetchMultiMerchantLogins();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        ArgumentCaptor<RowMapper<ProviderLoginRow>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(sql.capture(), params.capture(), mapper.capture());

        String text = sql.getValue().replaceAll("\\s+", " ");
        Assertions.assertTrue(text.contains("from TXPG.login l"), text);
        Assertions.assertTrue(text.contains("left join TXPG.login2merchant lm on lm.loginid = l.id"), text);
        Assertions.assertTrue(text.contains("left join TXPG.merchant m on m.id = lm.merchantid"), text);
        Assertions.assertTrue(text.contains("where l.ownerkind = :ownerKind"), text);
        Assertions.assertEquals("MultiMerchantSys", params.getValue().getValue("ownerKind"));
        Assertions.assertFalse(text.contains("terminal"), "a multimerchant login has no terminal: " + text);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("login")).thenReturn("bazarstore@company.com");
        when(rs.getString("login_status")).thenReturn("Active");
        when(rs.getString("link_status")).thenReturn("Active");
        when(rs.getString("merchant_rid")).thenReturn("223456789054323");
        when(rs.getString("merchant_title")).thenReturn("BazarStore PortBaku");
        Assertions.assertEquals(new ProviderLoginRow("bazarstore@company.com", "Active", "Active", "223456789054323",
                "BazarStore PortBaku"), mapper.getValue().mapRow(rs, 0));
    }
}
