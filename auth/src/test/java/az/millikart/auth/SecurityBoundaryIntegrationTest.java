package az.millikart.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.auth.domain.Company;
import az.millikart.auth.domain.User;
import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.UserRepository;
import az.millikart.common.security.JwtProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// P1-1 в auth: запрет по умолчанию, actuator вне публичного порта, swagger за флагом — и логин
// по-прежнему доступен без токена, потому что до входа предъявлять нечего.
// Тот же набор для pbl — в az.millikart.pbl.SecurityBoundaryIntegrationTest.
@SpringBootTest
@AutoConfigureMockMvc
class SecurityBoundaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String ADMIN_USERNAME = "boundary-admin@millikart.az";
    private static final String ADMIN_PASSWORD = "AdminPassword123!";

    private String adminToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        companyRepository.deleteAll();

        companyRepository.save(Company.builder()
                .id("comp-boundary")
                .name("Boundary LLC")
                .status("ACTIVE")
                .build());

        userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .username(ADMIN_USERNAME)
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .fullName("Boundary Admin")
                .role("SYSTEM_ADMIN")
                .status("ACTIVE")
                .build());

        adminToken = "Bearer " + jwtProvider.generateToken(
                "boundary-admin", ADMIN_USERNAME, "SYSTEM_ADMIN", null);
    }

    // Сам переворот

    // Главный тест P1-1: раньше неотображённый путь проходил Spring Security нетронутым.
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

    // Actuator вне публичного порта

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

    // Swagger выключен, пока не поднят флаг

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

    // Публичный путь по-прежнему работает

    @Test
    void login_withoutToken_stillWorks() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(ADMIN_USERNAME, ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));
    }

    // Браузер, наведённый на адрес API, или сканер: путь есть, глагола нет. До 20.08.2026 это
    // возвращалось как 500 "Unexpected server error" со строкой ERROR и стектрейсом, и вставленный
    // в адресную строку URL выглядел поломкой сервиса.
    @Test
    void login_withGet_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")))
                .andExpect(jsonPath("$.status", is(405)))
                .andExpect(jsonPath("$.message", containsString("POST")))
                .andExpect(jsonPath("$.path", is("/api/v1/auth/login")));
    }

    // На защищённом пути важен порядок: аутентификация отвечает первой, поэтому анонимный
    // вызывающий получает 401, а не 405 со списком глаголов эндпоинта. 405 — только для путей,
    // и без того публичных, вроде логина выше.
    @Test
    void protectedPath_withWrongMethod_andNoToken_stays401() throws Exception {
        mockMvc.perform(delete("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    // Та же ошибка слоем ниже: тело, которое эндпоинт не умеет читать, — это 415, а не 500.
    @Test
    void login_withFormContentType_returns415() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("username=a&password=b"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status", is(415)))
                .andExpect(jsonPath("$.message", containsString("application/json")));
    }

    // Всему остальному под /api/v1 нужен токен: /api/v1/auth/ не префикс для прочего.
    @Test
    void users_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    // Один формат отказа, кто бы его ни породил

    @Test
    void errorResponseShape_isConsistent() throws Exception {
        JsonNode fromFilter = body(mockMvc.perform(get("/api/v1/users"))
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
        Assertions.assertEquals("/api/v1/users", fromFilter.get("path").asText());
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
