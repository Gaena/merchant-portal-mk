package az.millikart.auth;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.auth.domain.Company;
import az.millikart.auth.domain.User;
import az.millikart.auth.dto.CreateUserRequest;
import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.UserRepository;
import az.millikart.common.security.JwtProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Р-132: право пользователя создавать DMS-ссылки. По умолчанию разрешено; запрещают только ролям компании —
// администратор любому, руководитель менеджерам и сотрудникам, себе не меняет. Правка пишется в журнал, а
// право уходит в access-токен claim'ом dmsLinks: по нему pbl отказывает в DMS-ссылке.
@SpringBootTest
@AutoConfigureMockMvc
class UserDmsLinksPermissionTest {

    private static final String PASSWORD = "UserPassword123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    private User head;
    private String headToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        TerminalFixture.ensureSchema(dataSource);
        TerminalFixture.clean(jdbcTemplate);
        userRepository.deleteAll();
        companyRepository.deleteAll();
        companyRepository.save(Company.builder().id("comp-01").name("MilliKart LLC").status("ACTIVE").build());
        head = saved("head@comp01.com", "COMPANY_HEAD", "comp-01");
        headToken = "Bearer " + jwtProvider.generateToken(head.getId().toString(), head.getUsername(),
                "COMPANY_HEAD", "comp-01", true);
        adminToken = "Bearer " + jwtProvider.generateToken(UUID.randomUUID().toString(), "admin@millikart.az",
                "SYSTEM_ADMIN", null, true);
    }

    @AfterEach
    void tearDown() {
        TerminalFixture.clean(jdbcTemplate);
    }

    // Без поля — разрешено: так заведены все до Р-132, и форма по умолчанию ставит галочку. Запрет при создании
    // пишется в журнал вместе с ролью.
    @Test
    void createdUsers_mayCreateDmsLinksUnlessForbidden() throws Exception {
        create(headToken, "manager@comp01.com", "COMPANY_MANAGER", "comp-01", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dmsLinksAllowed", is(true)));
        UUID forbidden = idOf(create(headToken, "clerk@comp01.com", "COMPANY_MANAGER", "comp-01", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dmsLinksAllowed", is(false))));

        Assertions.assertTrue(lastDetails(forbidden, "CREATE").endsWith(", DMS links forbidden"),
                lastDetails(forbidden, "CREATE"));
    }

    // Запрет и разрешение — правкой, с записью «с чего на что». Руководитель себе право не меняет, как роль и
    // статус: иначе запрет администратора снимался бы одним кликом. Администратор меняет и руководителю.
    @Test
    void headChangesItsPeople_butNotItself_andAdminChangesTheHead() throws Exception {
        User manager = saved("manager@comp01.com", "COMPANY_MANAGER", "comp-01");

        update(headToken, manager.getId(), dms(false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dmsLinksAllowed", is(false)));
        Assertions.assertEquals("Changed dmsLinksAllowed true -> false", lastDetails(manager.getId(), "UPDATE"));

        update(headToken, head.getId(), dms(false))
                .andExpect(status().isForbidden());
        Assertions.assertTrue(userRepository.findById(head.getId()).orElseThrow().isDmsLinksAllowed());

        update(adminToken, head.getId(), dms(false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dmsLinksAllowed", is(false)));
    }

    // У администратора и аудитора права на DMS нет смысла запрещать: администратор не проверяется вовсе, аудитор
    // ссылок не создаёт. Запрет им — 400, а ставший такой ролью получает true, чтобы запрет не всплыл при
    // возврате в компанию незамеченным.
    @Test
    void nonCompanyRoles_cannotBeForbidden_andGetTheRightBack() throws Exception {
        create(adminToken, "auditor@millikart.az", "AUDITOR", null, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("DMS links can be forbidden to company roles only")));

        User manager = saved("manager@comp01.com", "COMPANY_MANAGER", "comp-01");
        update(adminToken, manager.getId(), dms(false)).andExpect(status().isOk());
        update(adminToken, manager.getId(), new UpdateUserRequest(null, "AUDITOR", null, null, "", null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dmsLinksAllowed", is(true)));
    }

    // Право доходит до сервисов только токеном: вход выдаёт claim из строки пользователя.
    @Test
    void theAccessToken_carriesTheRight() throws Exception {
        User allowed = saved("allowed@comp01.com", "COMPANY_MANAGER", "comp-01");
        User forbidden = saved("forbidden@comp01.com", "COMPANY_MANAGER", "comp-01");
        forbidden.setDmsLinksAllowed(false);
        userRepository.save(forbidden);

        Assertions.assertEquals(Boolean.TRUE, dmsClaimAtLogin(allowed.getUsername()));
        Assertions.assertEquals(Boolean.FALSE, dmsClaimAtLogin(forbidden.getUsername()));
    }

    private Object dmsClaimAtLogin(String username) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("token").asText();
        return jwtProvider.validateAndGetClaims(token).get(JwtProvider.DMS_LINKS_CLAIM);
    }

    private User saved(String username, String role, String companyId) {
        return userRepository.save(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Test User")
                .role(role)
                .companyId(companyId)
                .status("ACTIVE")
                .build());
    }

    private static UpdateUserRequest dms(boolean allowed) {
        return new UpdateUserRequest(null, null, null, null, null, null, allowed);
    }

    private ResultActions create(String token, String username, String role, String companyId,
                                 Boolean dmsLinksAllowed) throws Exception {
        return mockMvc.perform(post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateUserRequest(username, PASSWORD, "Test User", role, companyId, null, dmsLinksAllowed))));
    }

    private ResultActions update(String token, UUID id, UpdateUserRequest body) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/" + id)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private UUID idOf(ResultActions created) throws Exception {
        JsonNode body = objectMapper.readTree(created.andReturn().getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    private String lastDetails(UUID userId, String action) {
        return jdbcTemplate.queryForObject(
                "SELECT details FROM audit_logs WHERE entity_id = ? AND action = ? ORDER BY created_at DESC LIMIT 1",
                String.class, userId.toString(), action);
    }
}
