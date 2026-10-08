package az.millikart.auth;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.auth.domain.Company;
import az.millikart.auth.domain.User;
import az.millikart.auth.dto.ChangePasswordRequest;
import az.millikart.auth.dto.CreateUserRequest;
import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.dto.RefreshRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// PCI DSS 8.3.5 (Р-100): пароль, заданный не владельцем, — при создании пользователя и при сбросе
// администратором — меняется при первом же входе, и до смены сессии нет. Цена ошибки — вход по паролю,
// который знает кто-то ещё. Запросы идут со своего адреса: общий лимит 127.0.0.1 другим тестам нужен целым.
@SpringBootTest
@AutoConfigureMockMvc
class PasswordChangeIntegrationTest {

    // Своя сеть (TEST-NET-1): 198.51.100.x и 203.0.113.x заняты тестами, которые нарочно исчерпывают лимит.
    private static final String CLIENT_ADDRESS = "192.0.2.100";
    private static final String ADMIN = "admin@millikart.az";
    private static final String ADMIN_PASSWORD = "AdminPassword123!";
    private static final String CLERK = "clerk@comp01.com";
    private static final String ISSUED_PASSWORD = "IssuedPassword123!";
    private static final String OWN_PASSWORD = "OwnPassword456!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;

    // Р-131: сотрудник заводится только с терминалом своей компании — терминалы из changelog directory.
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private javax.sql.DataSource dataSource;

    @org.junit.jupiter.api.AfterEach
    void cleanTerminals() {
        TerminalFixture.clean(jdbcTemplate);
    }

    @BeforeEach
    void setUp() throws Exception {
        TerminalFixture.ensureSchema(dataSource);
        TerminalFixture.clean(jdbcTemplate);
        userRepository.deleteAll();
        companyRepository.deleteAll();
        companyRepository.save(Company.builder().id("comp-01").name("MilliKart LLC").status("ACTIVE").build());
        userRepository.save(User.builder()
                .username(ADMIN)
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .fullName("System Admin")
                .role("SYSTEM_ADMIN")
                .status("ACTIVE")
                .build());
        adminToken = "Bearer " + body(login(ADMIN, ADMIN_PASSWORD)).get("token").asText();
    }

    @Test
    void aCreatedUser_getsNoSessionUntilTheIssuedPasswordIsChanged() throws Exception {
        createClerk();

        login(CLERK, ISSUED_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired", is(true)))
                .andExpect(jsonPath("$.role", is("COMPANY_EMPLOYEE")))
                .andExpect(jsonPath("$.token", nullValue()))
                .andExpect(jsonPath("$.refreshToken", nullValue()));

        // Смена — тот же вход: неверный текущий пароль считается неудачной попыткой.
        changePassword(CLERK, "WrongPassword123!", OWN_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid username or password")));
        assertEquals(1, clerk().getFailedLoginAttempts());
        changePassword(CLERK, ISSUED_PASSWORD, ISSUED_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("The new password must differ from the last 4 passwords")));
        changePassword(CLERK, ISSUED_PASSWORD, "short").andExpect(status().isBadRequest());
        assertTrue(clerk().isPasswordChangeRequired(), "a refused change leaves the requirement in place");

        changePassword(CLERK, ISSUED_PASSWORD, OWN_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired", is(false)))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        assertFalse(clerk().isPasswordChangeRequired());
        assertEquals(0, clerk().getFailedLoginAttempts());
        login(CLERK, ISSUED_PASSWORD).andExpect(status().isBadRequest());
        login(CLERK, OWN_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired", is(false)))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    // Сброс чужого пароля — снова обязательная смена, и сессии со старым паролем заканчиваются сразу:
    // сброс чаще всего и делают потому, что пароль утёк.
    @Test
    void anAdminReset_endsTheSessions_andRequiresAChangeAgain() throws Exception {
        UUID clerkId = createClerk();
        changePassword(CLERK, ISSUED_PASSWORD, OWN_PASSWORD).andExpect(status().isOk());
        String refreshToken = body(login(CLERK, OWN_PASSWORD)).get("refreshToken").asText();

        mockMvc.perform(patch("/api/v1/users/" + clerkId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest(null, null, "ResetPassword789!", null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired", is(true)));

        mockMvc.perform(fromClient(post("/api/v1/auth/refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
        login(CLERK, "ResetPassword789!").andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired", is(true)))
                .andExpect(jsonPath("$.token", nullValue()));
    }

    // Свой пароль владелец задаёт сам — второй смены это не требует. PATCH-SELF-PASSWORD: сессии со старым
    // паролем при этом гасились только у чужого сброса, и украденная сессия жила дальше; теперь гаснут все.
    @Test
    void changingYourOwnPasswordThroughTheUserApi_requiresNoFurtherChange() throws Exception {
        UUID adminId = userRepository.findByUsername(ADMIN).orElseThrow().getId();
        String stolenSession = body(login(ADMIN, ADMIN_PASSWORD)).get("refreshToken").asText();

        mockMvc.perform(patch("/api/v1/users/" + adminId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest(null, null, "AdminPassword456!", null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired", is(false)));

        mockMvc.perform(fromClient(post("/api/v1/auth/refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(stolenSession))))
                .andExpect(status().isUnauthorized());
        login(ADMIN, "AdminPassword456!").andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    // Смена по своей воле тоже заканчивает прочие сессии: они начаты со старым паролем.
    @Test
    void aVoluntaryChange_endsTheOtherSessions() throws Exception {
        String otherSession = body(login(ADMIN, ADMIN_PASSWORD)).get("refreshToken").asText();

        changePassword(ADMIN, ADMIN_PASSWORD, "AdminPassword789!").andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(fromClient(post("/api/v1/auth/refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(otherSession))))
                .andExpect(status().isUnauthorized());
    }

    private UUID createClerk() throws Exception {
        JsonNode created = body(mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(
                                CLERK, ISSUED_PASSWORD, "Clerk", "COMPANY_EMPLOYEE", "comp-01",
                                java.util.List.of(TerminalFixture.terminalOf(jdbcTemplate, "comp-01")), null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passwordChangeRequired", is(true))));
        return UUID.fromString(created.get("id").asText());
    }

    private User clerk() {
        return userRepository.findByUsername(CLERK).orElseThrow();
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(fromClient(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))));
    }

    private ResultActions changePassword(String username, String current, String next) throws Exception {
        return mockMvc.perform(fromClient(post("/api/v1/auth/change-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangePasswordRequest(username, current, next))));
    }

    private static MockHttpServletRequestBuilder fromClient(MockHttpServletRequestBuilder request) {
        return request.with(servletRequest -> {
            servletRequest.setRemoteAddr(CLIENT_ADDRESS);
            return servletRequest;
        });
    }

    private JsonNode body(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
