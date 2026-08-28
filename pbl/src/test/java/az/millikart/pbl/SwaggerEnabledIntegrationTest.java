package az.millikart.pbl;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// Вторая половина флага swagger: при SWAGGER_ENABLED=true документация обязана открываться без
// токена — так ею пользуются приёмочные тестировщики. Согласиться должны оба слоя: SecurityConfig
// добавляет матчеры, JwtAuthFilter пропускает пути; чти флаг только один из них, здесь был бы 401.
@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc
class SwaggerEnabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void swaggerUi_withFlagOn_isReachable() throws Exception {
        // springdoc отвечает на /swagger-ui.html редиректом на встроенный UI.
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void apiDocs_withFlagOn_isReachable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
    }

    // Включение документации не должно включать что-то ещё.
    @Test
    void unknownPath_withSwaggerOn_stillReturns401() throws Exception {
        mockMvc.perform(get("/some/unmapped/path"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedApi_withSwaggerOn_stillReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/payment-links"))
                .andExpect(status().isUnauthorized());
    }
}
