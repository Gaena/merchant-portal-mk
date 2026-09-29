package az.millikart.ecom.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

// Р-103: без адреса, логина или пароля базы шлюза ecom не стартует и называет переменную. Раньше пул был
// ленивым, сервис поднимался, а сбой всплывал ERROR'ом синхронизации раз в 15 минут.
class TxpgDataSourceConfigTest {

    @Test
    void anUnresolvedOrEmptyGatewaySetting_refusesToStart_andNamesTheVariable() {
        DataSourceProperties unresolvedUrl = properties("${ECOM_TXPG_URL}", "mp_ecom", "secret");
        DataSourceProperties emptyUser = properties("jdbc:oracle:thin:@//gw:1521/FREE", " ", "secret");
        DataSourceProperties noPassword = properties("jdbc:oracle:thin:@//gw:1521/FREE", "mp_ecom", null);

        assertTrue(assertThrows(IllegalStateException.class,
                () -> TxpgDataSourceConfig.requireGatewaySettings(unresolvedUrl)).getMessage().contains("ECOM_TXPG_URL"));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> TxpgDataSourceConfig.requireGatewaySettings(emptyUser)).getMessage().contains("ECOM_TXPG_USERNAME"));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> TxpgDataSourceConfig.requireGatewaySettings(noPassword)).getMessage().contains("ECOM_TXPG_PASSWORD"));
    }

    @Test
    void completeGatewaySettings_pass() {
        assertDoesNotThrow(() -> TxpgDataSourceConfig.requireGatewaySettings(
                properties("jdbc:oracle:thin:@//gw:1521/FREE", "mp_ecom", "secret")));
    }

    private static DataSourceProperties properties(String url, String username, String password) {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl(url);
        properties.setUsername(username);
        properties.setPassword(password);
        return properties;
    }
}
