package az.millikart.pbl;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.common.security.JwtProvider;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import az.millikart.pbl.provider.StubAcquirerConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// P1-1: умолчание перевернули с «разрешено, пока фильтр не возразит» на «нужна аутентификация,
// если PublicEndpoints не сказал иного». Раньше стоял anyRequest().permitAll(), и единственной
// преградой была проверка префикса внутри JwtAuthFilter: всё вне /api/v1/, включая actuator и
// swagger, было публичным. Верни permitAll обратно — первый тест этого класса покраснеет.
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubAcquirerConfig.class)
class SecurityBoundaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TerminalRepository terminalRepository;

    @Autowired
    private PaymentLinkRepository paymentLinkRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    // Свой диапазон, чтобы фикстуры не сталкивались с другими интеграционными тестами.
    private static final int TERMINAL_ID = 770101;

    private String headToken;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        paymentLinkRepository.deleteAll();
        terminalRepository.deleteAll();

        terminalRepository.save(Terminal.builder()
                .id(TERMINAL_ID)
                .name("Security Boundary Terminal")
                .login("TerminalSys/Boundary")
                .password("1234")
                .companyId("boundary-company")
                .build());

        headToken = "Bearer " + jwtProvider.generateToken(
                "boundary-head", "boundary-head@test.com", "COMPANY_HEAD", "boundary-company");
    }

    // Сам переворот

    // Главный тест P1-1: незамапленный путь раньше проходил Spring Security нетронутым и падал
    // только позже, в диспетчере. Теперь отказ наступает до того, как кто-то на него посмотрит.
    @Test
    void unknownPath_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/some/unmapped/path"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/some/unmapped/path"));
    }

    // С токеном того же пути просто нет: 401 выше — про учётные данные, а не про маршрутизацию.
    @Test
    void unknownPath_withToken_returns404() throws Exception {
        mockMvc.perform(get("/some/unmapped/path").header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // Запрет по умолчанию покрывает методы и префиксы, о которых никто не думал, не только GET.
    @Test
    void unknownPathUnderApiPrefix_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v2/anything").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // Actuator недоступен на публичном порту

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

    @Test
    void actuatorInfo_onMainPort_isNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isNotFound());
    }

    // Swagger выключен, пока не поднят флаг

    @Test
    void swaggerUi_withFlagOff_returns404() throws Exception {
        mockMvc.perform(get("/swagger-ui.html").header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiDocs_withFlagOff_returns404() throws Exception {
        mockMvc.perform(get("/v3/api-docs").header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isNotFound());
    }

    // Анонимно те же пути не доходят даже до «не найдено».
    @Test
    void apiDocs_withFlagOff_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
    }

    // Публичные пути продолжают работать

    @Test
    void openPaymentLink_withoutToken_stillRedirectsToProvider() throws Exception {
        UUID id = createLink();

        mockMvc.perform(get("/api/v1/payment-links/{id}/open", id))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("rid=")));
    }

    @Test
    void redirectPage_withoutToken_stillRenders() throws Exception {
        mockMvc.perform(get("/api/v1/payment-links/redirect/{tx}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("unavailable")));
    }

    // Намеренное ужесточение. Старая проверка звучала как «под /api/v1/payment-links/ и кончается
    // на /open» и совпадала на любой глубине; теперь шаблон требует ровно один сегмент, как и сам
    // маппинг GetMapping("/{id}/open"). Лишний сегмент больше не способ дойти куда-то анонимно.
    @Test
    void openWithExtraPathSegment_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payment-links/a/b/open"))
                .andExpect(status().isUnauthorized());
    }

    // Обратная сторона: законная форма из одного сегмента анонимна даже для несуществующего id.
    @Test
    void openWithSingleSegment_withoutToken_isNotRejectedBySecurity() throws Exception {
        mockMvc.perform(get("/api/v1/payment-links/{id}/open", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Payment link")));
    }

    // Один формат отказа, кто бы его ни выдал

    // 401 от JwtAuthFilter и 401 от entry point Spring Security обязаны быть неотличимы: те же
    // поля, тот же порядок, те же значения, то же представление времени.
    @Test
    void errorResponseShape_isConsistent() throws Exception {
        // Отказ от JwtAuthFilter: замапленный защищённый путь без заголовка Authorization.
        JsonNode fromFilter = body(mockMvc.perform(get("/api/v1/payment-links"))
                .andExpect(status().isUnauthorized()));

        // Отказ от Spring Security: здесь ничего не замаплено, фильтр не отрабатывает.
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

        Assertions.assertEquals("/api/v1/payment-links", fromFilter.get("path").asText());
        Assertions.assertEquals("/some/unmapped/path", fromSecurity.get("path").asText());

        // Оба проходят через ObjectMapper приложения, поэтому оба — текст ISO-8601, а не сырое
        // число эпохи у одного из них.
        Assertions.assertTrue(fromFilter.get("timestamp").isTextual());
        Assertions.assertTrue(fromSecurity.get("timestamp").isTextual());
        Assertions.assertDoesNotThrow(() -> Instant.parse(fromFilter.get("timestamp").asText()));
        Assertions.assertDoesNotThrow(() -> Instant.parse(fromSecurity.get("timestamp").asText()));
    }

    // Хелперы

    private UUID createLink() throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("merchantOrderId", "ORDER-BOUNDARY-1");
        request.put("terminal", TERMINAL_ID);
        request.put("amount", new BigDecimal("10.00"));
        request.put("currency", "AZN");
        request.put("paymentType", "SMS");
        request.put("usageType", "SINGLE");

        String response = mockMvc.perform(post("/api/v1/payment-links")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
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
