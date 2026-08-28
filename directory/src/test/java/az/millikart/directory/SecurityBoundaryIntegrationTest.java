package az.millikart.directory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.common.security.JwtProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// P1-1 в directory. Публичного API у сервиса нет вовсе: токен нужен на каждом пути, поверхность
// целиком «запрещено по умолчанию», а анонимно отвечают только те пути, которых здесь больше нет —
// actuator ушёл на management-порт, swagger спрятан за флагом. Тот же набор для pbl лежит в
// az.millikart.pbl.SecurityBoundaryIntegrationTest.
@SpringBootTest
@AutoConfigureMockMvc
class SecurityBoundaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProvider jwtProvider;

    private String adminToken;

    @BeforeEach
    void setUp() {
        adminToken = "Bearer " + jwtProvider.generateToken(
                "boundary-admin", "boundary-admin@test.com", "SYSTEM_ADMIN", null);
    }

    // --- сам переворот ---

    // Главный тест P1-1: незамапленный путь раньше проходил Spring Security нетронутым.
    @Test
    void unknownPath_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/some/unmapped/path"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/some/unmapped/path"));
    }

    @Test
    void unknownPath_withToken_returns404() throws Exception {
        mockMvc.perform(get("/some/unmapped/path").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    // --- actuator убран с публичного порта ---

    @Test
    void actuatorHealth_onMainPort_isNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
    }

    @Test
    void actuatorMetrics_onMainPort_isNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isNotFound());
    }

    // --- swagger выключен, пока не поднят флаг ---

    @Test
    void swaggerUi_withFlagOff_returns404() throws Exception {
        mockMvc.perform(get("/swagger-ui.html").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiDocs_withFlagOff_returns404() throws Exception {
        mockMvc.perform(get("/v3/api-docs").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiDocs_withFlagOff_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
    }

    // --- публичного здесь нет ничего ---

    @Test
    void companies_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/companies"))
                .andExpect(status().isUnauthorized());
    }

    // Публичные паттерны принадлежат другим сервисам и не должны протечь сюда через общую
    // конфигурацию: directory под ними ничего не мапит, а без токена они отклоняются.
    @Test
    void pblPublicPatterns_areNotReachableHere() throws Exception {
        mockMvc.perform(get("/api/v1/payment-links/{id}/open", "some-id"))
                .andExpect(status().isNotFound());
    }

    // --- один формат отказа, кто бы его ни выдал ---

    @Test
    void errorResponseShape_isConsistent() throws Exception {
        JsonNode fromFilter = body(mockMvc.perform(get("/api/v1/companies"))
                .andExpect(status().isUnauthorized()));
        JsonNode fromSecurity = body(mockMvc.perform(get("/some/unmapped/path"))
                .andExpect(status().isUnauthorized()));

        Assertions.assertEquals(List.of("timestamp", "status", "error", "message", "path"),
                fieldNames(fromFilter));
        Assertions.assertEquals(fieldNames(fromFilter), fieldNames(fromSecurity));

        Assertions.assertEquals(401, fromFilter.get("status").asInt());
        Assertions.assertEquals(401, fromSecurity.get("status").asInt());
        Assertions.assertEquals("Unauthorized", fromFilter.get("error").asText());
        Assertions.assertEquals("Unauthorized", fromSecurity.get("error").asText());
        Assertions.assertEquals(fromFilter.get("message").asText(), fromSecurity.get("message").asText());
        Assertions.assertEquals("/api/v1/companies", fromFilter.get("path").asText());
        Assertions.assertEquals("/some/unmapped/path", fromSecurity.get("path").asText());

        Assertions.assertTrue(fromFilter.get("timestamp").isTextual());
        Assertions.assertTrue(fromSecurity.get("timestamp").isTextual());
        Assertions.assertDoesNotThrow(() -> Instant.parse(fromFilter.get("timestamp").asText()));
        Assertions.assertDoesNotThrow(() -> Instant.parse(fromSecurity.get("timestamp").asText()));
    }

    private JsonNode body(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
