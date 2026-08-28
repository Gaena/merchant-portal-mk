package az.millikart.pbl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

// Единственный тест, которому нужен настоящий контейнер: MockMvc поднимает лишь основной порт и
// management-контекст не стартует. Вопрос — отвечает ли /actuator/health на management-порту без
// токена: обязан, иначе ломаются liveness-пробы, они не шлют учётных данных. Защищает их не
// аутентификация, а management.server.address: 127.0.0.1; порт 0 — чтобы не занять чужой.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.server.address=127.0.0.1",
        "management.endpoints.web.exposure.include=health,info,metrics",
        "management.endpoint.health.show-details=always"
})
class ManagementPortIntegrationTest {

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void managementPort_isSeparateFromTheServicePort() {
        Assertions.assertNotEquals(serverPort, managementPort);
    }

    @Test
    void actuatorHealth_onManagementPort_answersWithoutToken() {
        ResponseEntity<String> response = get(managementPort, "/actuator/health");

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());
        Assertions.assertTrue(response.getBody().contains("\"status\":\"UP\""), response.getBody());
        // show-details: always оставлен включённым именно потому, что порт слушает только loopback.
        Assertions.assertTrue(response.getBody().contains("components"), response.getBody());
    }

    @Test
    void actuatorMetrics_onManagementPort_answersWithoutToken() {
        Assertions.assertEquals(HttpStatus.OK, get(managementPort, "/actuator/metrics").getStatusCode());
    }

    @Test
    void actuatorHealth_onServicePort_isNotExposed() {
        ResponseEntity<String> response = get(serverPort, "/actuator/health");

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void actuatorMetrics_onServicePort_isNotExposed() {
        Assertions.assertEquals(HttpStatus.NOT_FOUND, get(serverPort, "/actuator/metrics").getStatusCode());
    }

    // Запрет по умолчанию держится и в настоящем контейнере, не только под MockMvc.
    @Test
    void unknownPath_onServicePort_withoutToken_returns401() {
        ResponseEntity<String> response = get(serverPort, "/some/unmapped/path");

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());
        Assertions.assertTrue(response.getBody().contains("\"status\":401"), response.getBody());
    }

    private ResponseEntity<String> get(int port, String path) {
        return rest.getForEntity("http://127.0.0.1:" + port + path, String.class);
    }
}
