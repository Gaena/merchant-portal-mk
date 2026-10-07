package az.millikart.auth;

import az.millikart.auth.domain.Company;
import az.millikart.auth.domain.User;
import az.millikart.auth.dto.ChangePasswordRequest;
import az.millikart.auth.dto.CreateUserRequest;
import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.List;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationContext applicationContext;

    // Миграция больше не заводит админа (P0-6), поэтому фикстура создаёт своего. Пароль отвечает
    // той же политике, которую API требует от любого аккаунта.
    private static final String ADMIN_PASSWORD = "AdminPassword123!";

    // Единственный ответ на любой отказ во входе — см. AuthService.INVALID_CREDENTIALS.
    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    // auth.login.rate-limit.max-failures в тестовом профиле, как в production.
    private static final int MAX_FAILURES_PER_ADDRESS = 10;

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
    public void setup() throws Exception {
        TerminalFixture.ensureSchema(dataSource);
        TerminalFixture.clean(jdbcTemplate);
        userRepository.deleteAll();
        companyRepository.deleteAll();

        Company company = Company.builder()
                .id("comp-01")
                .name("MilliKart LLC")
                .status("ACTIVE")
                .build();
        companyRepository.save(company);

        User admin = User.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                .username("admin@millikart.az")
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .fullName("System Admin")
                .role("SYSTEM_ADMIN")
                .status("ACTIVE")
                .build();
        userRepository.save(admin);

        LoginRequest loginRequest = new LoginRequest("admin@millikart.az", ADMIN_PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        adminToken = "Bearer " + objectMapper.readTree(responseBody).get("token").asText();
    }

    @Test
    public void testLogin_Success() throws Exception {
        LoginRequest loginRequest = new LoginRequest("admin@millikart.az", ADMIN_PASSWORD);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.role", is("SYSTEM_ADMIN")));
    }

    @Test
    public void testLogin_InvalidCredentials() throws Exception {
        LoginRequest loginRequest = new LoginRequest("admin@millikart.az", "wrongpass");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid username or password")));
    }

    @Test
    public void testLogin_InvalidEmailFormat() throws Exception {
        LoginRequest loginRequest = new LoginRequest("not-an-email-address", "HeadPassword123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Username must be a valid email address")));
    }

    @Test
    public void testUserCRUD_Success() throws Exception {
        CreateUserRequest createHead = new CreateUserRequest(
                "head@comp01.com",
                "HeadPassword123!",
                "Company Head User",
                "COMPANY_HEAD",
                "comp-01"
        , null, null);

        MvcResult createResult = mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createHead)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("head@comp01.com")))
                .andExpect(jsonPath("$.role", is("COMPANY_HEAD")))
                .andExpect(jsonPath("$.companyId", is("comp-01")))
                .andReturn();

        String headIdStr = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();
        UUID headId = UUID.fromString(headIdStr);

        // Пароль задал администратор: вход без сессии, сессия — после смены (Р-100).
        LoginRequest headLogin = new LoginRequest("head@comp01.com", "HeadPassword123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(headLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired", is(true)))
                .andExpect(jsonPath("$.token").value(org.hamcrest.Matchers.nullValue()));
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest("head@comp01.com", "HeadPassword123!", "HeadPassword456!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired", is(false)))
                .andReturn();

        String headToken = "Bearer " + objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();

        CreateUserRequest createEmp = new CreateUserRequest(
                "emp@comp01.com",
                "EmployeePass123!",
                "Employee User",
                "COMPANY_EMPLOYEE",
                "comp-01"
        , List.of(TerminalFixture.terminalOf(jdbcTemplate, "comp-01")), null);

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createEmp)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("emp@comp01.com")))
                .andExpect(jsonPath("$.companyId", is("comp-01")));

        // COMPANY_HEAD не может завести пользователя в чужой или несуществующей компании.
        CreateUserRequest createOtherCompanyEmp = new CreateUserRequest(
                "other@comp.com",
                "EmployeePass123!",
                "Other User",
                "COMPANY_EMPLOYEE",
                "comp-different"
        , null, null);

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOtherCompanyEmp)))
                .andExpect(status().isForbidden());

        // С P2-1 список страничный: строки лежат в content, счётчик — в totalElements.
        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(2)));

        UpdateUserRequest updateRequest = new UpdateUserRequest("Updated Name", null, "NewSecurePass123!", "ACTIVE", null, null, null);
        mockMvc.perform(patch("/api/v1/users/" + headId)
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("Updated Name")));

        LoginRequest updatedLogin = new LoginRequest("head@comp01.com", "NewSecurePass123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedLogin)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/users/" + headId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/" + headId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isBadRequest());
    }

    private static final String USER_PASSWORD = "UserPassword123!";

    // Руководитель выдаёт только роли ниже своей (auth.md §4.2): AUDITOR читает данные всех компаний.
    @Test
    public void companyHead_cannotGrantAuditorRole() throws Exception {
        createUser("head2@comp01.com", "COMPANY_HEAD", "comp-01");
        String headToken = login("head2@comp01.com");

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(
                                "spy@comp01.com", USER_PASSWORD, "Spy", "AUDITOR", "comp-01", null, null))))
                .andExpect(status().isForbidden());
    }

    // Администратор с companyId руководителя — не его подчинённый: сменить ему пароль значило бы
    // войти администратором.
    @Test
    public void companyHead_cannotChangePasswordOfAnAdminOfHisCompany() throws Exception {
        UUID secondAdminId = createUser("admin2@millikart.az", "SYSTEM_ADMIN", "comp-01");
        createUser("head3@comp01.com", "COMPANY_HEAD", "comp-01");
        String headToken = login("head3@comp01.com");

        mockMvc.perform(patch("/api/v1/users/" + secondAdminId)
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRequest(null, null, "Takeover12345!", null, null, null, null))))
                .andExpect(status().isForbidden());
    }

    // Заблокированный руководитель, пока жив его access-токен, не снимает блокировку сам с себя.
    @Test
    public void blockedHead_cannotUnblockHimselfWithALiveToken() throws Exception {
        UUID headId = createUser("head4@comp01.com", "COMPANY_HEAD", "comp-01");
        String headToken = login("head4@comp01.com");

        mockMvc.perform(patch("/api/v1/users/" + headId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRequest(null, null, null, "BLOCKED", null, null, null))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/users/" + headId)
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRequest(null, null, null, "ACTIVE", null, null, null))))
                .andExpect(status().isForbidden());
        Assertions.assertEquals("BLOCKED", userRepository.findById(headId).orElseThrow().getStatus());
    }

    // ─── Р-90: смена компании ────────────────────────────────────────────────

    // Пользователя, заведённого не в ту компанию, администратор переводит правкой, а не удалением и
    // повторным заведением. Роль и компания меняются одним запросом.
    @Test
    public void admin_movesAUserToAnotherCompany_andChangesTheRole() throws Exception {
        saveCompany("comp-02");
        UUID userId = createUser("wrong@comp01.com", "COMPANY_EMPLOYEE", "comp-01");

        mockMvc.perform(patch("/api/v1/users/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest(null, "COMPANY_MANAGER", null, null, "comp-02", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId", is("comp-02")))
                .andExpect(jsonPath("$.role", is("COMPANY_MANAGER")));

        User moved = userRepository.findById(userId).orElseThrow();
        Assertions.assertEquals("comp-02", moved.getCompanyId());
        Assertions.assertEquals("COMPANY_MANAGER", moved.getRole());
    }

    // Руководитель правит людей только своей компании и перевести их в чужую не может: иначе он
    // отдавал бы сотрудника с доступом к своим данным в компанию, которую не видит.
    @Test
    public void companyHead_cannotMoveAUserToAnotherCompany() throws Exception {
        saveCompany("comp-02");
        createUser("head5@comp01.com", "COMPANY_HEAD", "comp-01");
        UUID employeeId = createUser("emp5@comp01.com", "COMPANY_EMPLOYEE", "comp-01");
        String headToken = login("head5@comp01.com");

        mockMvc.perform(patch("/api/v1/users/" + employeeId)
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest(null, null, null, null, "comp-02", null, null))))
                .andExpect(status().isForbidden());
        Assertions.assertEquals("comp-01", userRepository.findById(employeeId).orElseThrow().getCompanyId());

        // Та же компания — не перевод: запрос с нетронутым полем проходит.
        mockMvc.perform(patch("/api/v1/users/" + employeeId)
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest("Renamed", null, null, null, "comp-01", null, null))))
                .andExpect(status().isOk());
    }

    @Test
    public void movingToACompanyThatDoesNotExist_isRefused() throws Exception {
        UUID userId = createUser("emp6@comp01.com", "COMPANY_EMPLOYEE", "comp-01");

        mockMvc.perform(patch("/api/v1/users/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest(null, null, null, null, "no-such-company", null, null))))
                .andExpect(status().isBadRequest());
        Assertions.assertEquals("comp-01", userRepository.findById(userId).orElseThrow().getCompanyId());
    }

    // Роль компании без компании бессмысленна: ни одного своего терминала. Снять компанию можно только
    // вместе со сменой роли на роль вне компании, и весь запрос откатывается, если итог неверен.
    @Test
    public void aCompanyRoleCannotBeLeftWithoutACompany() throws Exception {
        UUID headId = createUser("head7@comp01.com", "COMPANY_HEAD", "comp-01");

        mockMvc.perform(patch("/api/v1/users/" + headId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest("Should Not Stick", null, null, null, "", null, null))))
                .andExpect(status().isBadRequest());
        User untouched = userRepository.findById(headId).orElseThrow();
        Assertions.assertEquals("comp-01", untouched.getCompanyId());
        Assertions.assertEquals("Test User", untouched.getFullName(), "the whole request is rolled back");

        mockMvc.perform(patch("/api/v1/users/" + headId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest(null, "AUDITOR", null, null, "", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").doesNotExist());
    }

    private void saveCompany(String id) {
        companyRepository.save(Company.builder().id(id).name("Company " + id).status("ACTIVE").build());
    }

    // Р-103: роль компании без компании — отказ и при создании, как в правке (Р-90). Администратор и
    // аудитор без компании — нормальный случай.
    @Test
    public void createUser_withACompanyRoleButNoCompany_isRefused() throws Exception {
        for (String companyId : new String[] {null, ""}) {
            mockMvc.perform(post("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreateUserRequest(
                                    "nocompany@comp01.com", USER_PASSWORD, "No Company", "COMPANY_EMPLOYEE", companyId, null, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", is("Role COMPANY_EMPLOYEE requires a company")));
        }
        Assertions.assertTrue(userRepository.findByUsername("nocompany@comp01.com").isEmpty());

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(
                                "auditor@millikart.az", USER_PASSWORD, "Auditor", "AUDITOR", null, null, null))))
                .andExpect(status().isCreated());
    }

    // DB-CONSTRAINT-500: пустая строка уходила в users.company_id как есть, падала на внешнем ключе к
    // companies, и администратор получал 500. Пустая компания — «без компании», как в правке.
    @Test
    public void createUser_auditorWithAnEmptyCompany_isCreatedWithoutOne() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(
                                "auditor2@millikart.az", USER_PASSWORD, "Auditor", "AUDITOR", "", null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").doesNotExist());

        Assertions.assertNull(userRepository.findByUsername("auditor2@millikart.az").orElseThrow().getCompanyId());
    }

    // USER-EMPTY-PASSWORD: форма правки шлёт пустой пароль как «не менять», сервис так его и понимает, а
    // проверка политики отвечала 400 на пустую строку — и правка имени срывалась.
    @Test
    public void updateUser_withAnEmptyPassword_changesTheRestAndKeepsThePassword() throws Exception {
        UUID clerkId = createUser("clerk-empty@comp01.com", "COMPANY_EMPLOYEE", "comp-01");
        String hashBefore = userRepository.findById(clerkId).orElseThrow().getPasswordHash();

        mockMvc.perform(patch("/api/v1/users/" + clerkId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Renamed Clerk\", \"password\": \"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("Renamed Clerk")))
                .andExpect(jsonPath("$.passwordChangeRequired", is(false)));

        Assertions.assertEquals(hashBefore, userRepository.findById(clerkId).orElseThrow().getPasswordHash());
    }

    // DB-CONSTRAINT-500: имя длиннее колонки проходило проверку DTO и роняло вставку — 500 и ERROR.
    @Test
    public void createUser_withAFullNameLongerThanTheColumn_isABadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(
                                "longname@comp01.com", USER_PASSWORD, "x".repeat(256), "COMPANY_EMPLOYEE", "comp-01", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Full name must be at most 255 characters")));

        Assertions.assertTrue(userRepository.findByUsername("longname@comp01.com").isEmpty());
    }

    // Р-103: правкой ставятся только ACTIVE и BLOCKED. DELETED через PATCH удалял бы в обход DELETE и
    // его записи в журнале, а незнакомое значение ни один экран не прочтёт.
    @Test
    public void updateUser_statusIsOnlyActiveOrBlocked() throws Exception {
        UUID userId = createUser("status@comp01.com", "COMPANY_EMPLOYEE", "comp-01");

        for (String status : new String[] {"DELETED", "INACTIVE", "active", " BLOCKED"}) {
            mockMvc.perform(patch("/api/v1/users/" + userId)
                            .header(HttpHeaders.AUTHORIZATION, adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateUserRequest(null, null, null, status, null, null, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", is("User status must be ACTIVE or BLOCKED")));
        }
        Assertions.assertEquals("ACTIVE", userRepository.findById(userId).orElseThrow().getStatus());

        mockMvc.perform(patch("/api/v1/users/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRequest(null, null, null, "BLOCKED", null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("BLOCKED")));
    }

    // Удалённая компания — как несуществующая (Р-107): строка в companies осталась, но ни завести в неё
    // пользователя, ни перевести его туда нельзя.
    @Test
    public void aDeletedCompany_takesNoNewOrMovedUsers() throws Exception {
        companyRepository.save(Company.builder().id("comp-gone").name("Gone LLC").status("DELETED").build());
        UUID userId = createUser("mover@comp01.com", "COMPANY_EMPLOYEE", "comp-01");

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(
                                "newcomer@gone.com", USER_PASSWORD, "Newcomer", "COMPANY_EMPLOYEE", "comp-gone", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Company not found")));
        Assertions.assertTrue(userRepository.findByUsername("newcomer@gone.com").isEmpty());

        mockMvc.perform(patch("/api/v1/users/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRequest(null, null, null, null, "comp-gone", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Company not found")));
        Assertions.assertEquals("comp-01", userRepository.findById(userId).orElseThrow().getCompanyId());
    }

    // Пользователь, уже сменивший выданный пароль (Р-100): тесты здесь о правах, а не о первом входе.
    private UUID createUser(String username, String role, String companyId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(
                                username, USER_PASSWORD, "Test User", role, companyId,
                                "COMPANY_EMPLOYEE".equals(role)
                                        ? List.of(TerminalFixture.terminalOf(jdbcTemplate, companyId)) : null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        User user = userRepository.findById(id).orElseThrow();
        user.setPasswordChangeRequired(false);
        userRepository.save(user);
        return id;
    }

    private String login(String username) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, USER_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("token").asText();
    }

    // 7-я попытка с неверным паролем получает тот же безымянный отказ, что и шесть до неё: сказать
    // "заблокировано" тому, кто пароля не знает, — значит подтвердить, что аккаунт существует.
    // Что блокировка действительно произошла, видно только с верным паролем — это соседний тест
    // lockedOutAccount_withTheRightPassword_isToldAboutTheLockout.
    @Test
    public void testAccountLockout_After6FailedAttempts() throws Exception {
        LoginRequest invalidLogin = new LoginRequest("admin@millikart.az", "WrongPass123!");

        for (int i = 1; i <= 6; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidLogin)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", is(INVALID_CREDENTIALS)));
        }

        // Аккаунт заблокирован на 30 минут (PCI-DSS 8.3.4) — без изменений, Р-28.
        User locked = userRepository.findByUsername("admin@millikart.az").orElseThrow();
        Assertions.assertEquals(6, locked.getFailedLoginAttempts());
        Assertions.assertNotNull(locked.getLockoutUntil(), "the 6th failure must set a lockout");
        Assertions.assertTrue(locked.getLockoutUntil().isAfter(Instant.now().plus(25, ChronoUnit.MINUTES)),
                "the lockout must last about 30 minutes");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLogin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(INVALID_CREDENTIALS)));

        // Блокировка при этом не сдвинулась вперёд: 30 минут от 6-го отказа, Р-28.
        Assertions.assertEquals(locked.getLockoutUntil(),
                userRepository.findByUsername("admin@millikart.az").orElseThrow().getLockoutUntil(),
                "attempts made while locked out must not re-arm the lockout");
    }

    // P3-Auth: перечисление аккаунтов и лимит попыток на адрес. Каждый тест ниже задаёт свой адрес
    // (setRemoteAddr): лимитер ключуется по адресу, поэтому адрес на тест — граница изоляции,
    // которой общий Spring-контекст сам не даёт; к тому же 127.0.0.1, откуда логинятся остальные
    // тесты, входит в mp.trusted-proxies, и его forwarding-заголовкам бы поверили.

    // Суть: "нет такого пользователя" и "неверный пароль" должны быть неразличимы.
    @Test
    @DisplayName("13. an unknown username and a wrong password answer identically")
    public void unknownUsernameAndWrongPassword_answerIdentically() throws Exception {
        ObjectNode unknownUser = errorBody(login("nobody@millikart.az", "WrongPass123!", "203.0.113.13"));
        ObjectNode wrongPassword = errorBody(login("admin@millikart.az", "WrongPass123!", "203.0.113.13"));

        Assertions.assertEquals(unknownUser, wrongPassword,
                "the two answers differ, so the endpoint says which of our merchants have accounts");
        Assertions.assertEquals(INVALID_CREDENTIALS, unknownUser.get("message").asText());
    }

    @Test
    @DisplayName("14. a blocked account with a wrong password gets the same anonymous refusal")
    public void blockedAccount_withAWrongPassword_getsTheGeneralRefusal() throws Exception {
        seedUser("blocked@comp01.com", "BlockedPass123!", "BLOCKED");

        MvcResult result = login("blocked@comp01.com", "WrongPass123!", "203.0.113.14")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(INVALID_CREDENTIALS)))
                .andReturn();

        assertSaysNothingAboutTheStatus(result);
    }

    // Пароль верный, значит это владелец аккаунта и он заслуживает объяснения — но объяснение
    // всё равно не называет статус: BLOCKED и DELETED — разные факты о человеке, и нужны они
    // администратору.
    @Test
    @DisplayName("15. a blocked account with the right password is told the account is not active, without the status")
    public void blockedAccount_withTheRightPassword_isToldItIsNotActive() throws Exception {
        seedUser("blocked@comp01.com", "BlockedPass123!", "BLOCKED");

        MvcResult result = login("blocked@comp01.com", "BlockedPass123!", "203.0.113.15")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Account is not active. Please contact your administrator.")))
                .andReturn();

        assertSaysNothingAboutTheStatus(result);
    }

    @Test
    @DisplayName("16. an account already locked out, wrong password → the general refusal")
    public void lockedOutAccount_withAWrongPassword_getsTheGeneralRefusal() throws Exception {
        lockOut("admin@millikart.az");

        login("admin@millikart.az", "WrongPass123!", "203.0.113.16")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(INVALID_CREDENTIALS)));
    }

    // Вторая половина пары: доказательство, что блокировка реальна, и принятая остаточная утечка.
    @Test
    @DisplayName("16b. an account already locked out, right password → told about the lockout, with the time")
    public void lockedOutAccount_withTheRightPassword_isToldAboutTheLockout() throws Exception {
        lockOut("admin@millikart.az");

        login("admin@millikart.az", ADMIN_PASSWORD, "203.0.113.16")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Account is locked due to multiple failed login attempts.")))
                .andExpect(jsonPath("$.message", containsString("minutes")));
    }

    // Истёкший локаут обнуляет счётчик: без сброса неудача после блокировки была бы седьмой, и первая же
    // опечатка снова закрывала бы аккаунт на 30 минут (Р-28).
    @Test
    @DisplayName("16c. after the lockout has expired, a wrong password counts from one again")
    public void expiredLockout_wrongPassword_startsTheCountAfresh() throws Exception {
        expiredLockOut("admin@millikart.az");

        login("admin@millikart.az", "WrongPass123!", "203.0.113.161")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(INVALID_CREDENTIALS)));

        User user = userRepository.findByUsername("admin@millikart.az").orElseThrow();
        Assertions.assertEquals(1, user.getFailedLoginAttempts());
        Assertions.assertNull(user.getLockoutUntil(), "one typo after the lockout must not lock the account again");
    }

    @Test
    @DisplayName("16d. after the lockout has expired, the right password signs in and clears the count")
    public void expiredLockout_rightPassword_signsIn() throws Exception {
        expiredLockOut("admin@millikart.az");

        login("admin@millikart.az", ADMIN_PASSWORD, "203.0.113.162").andExpect(status().isOk());

        User user = userRepository.findByUsername("admin@millikart.az").orElseThrow();
        Assertions.assertEquals(0, user.getFailedLoginAttempts());
        Assertions.assertNull(user.getLockoutUntil());
    }

    @Test
    @DisplayName("17. past the per-address limit → 429 with Retry-After")
    public void tooManyFailures_areRefusedWith429AndRetryAfter() throws Exception {
        String clientIp = "198.51.100.17";
        for (int i = 0; i < MAX_FAILURES_PER_ADDRESS; i++) {
            login("admin@millikart.az", "WrongPass123!", clientIp).andExpect(status().isBadRequest());
        }

        login("admin@millikart.az", "WrongPass123!", clientIp)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.message", is("Too many login attempts. Please try again later.")))
                .andExpect(jsonPath("$.status", is(429)));
    }

    // Лимит срабатывает до поиска пользователя, поэтому подбор логинов стоит столько же, сколько
    // подбор паролей. Иначе аккаунты перечисляли бы бесплатно и встречали лимитер только тогда,
    // когда имя для атаки уже найдено.
    @Test
    @DisplayName("18. the limit also refuses attempts against usernames that do not exist")
    public void tooManyFailures_againstAnUnknownUsername_areAlsoRefused() throws Exception {
        String clientIp = "198.51.100.18";
        for (int i = 0; i < MAX_FAILURES_PER_ADDRESS; i++) {
            login("nobody" + i + "@millikart.az", "WrongPass123!", clientIp).andExpect(status().isBadRequest());
        }

        login("nobody-final@millikart.az", "WrongPass123!", clientIp)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    // P2-10 снаружи: узел не доверенный прокси, его X-Forwarded-For игнорируется, и новое значение
    // в каждом запросе ничего не даёт. До фикса каждый поддельный заголовок открывал бы свой
    // счётчик, и до 429 дело не дошло бы никогда.
    @Test
    @DisplayName("19. a forged X-Forwarded-For does not buy a fresh counter")
    public void forgedForwardedForHeader_doesNotCreateANewCounter() throws Exception {
        String clientIp = "198.51.100.19";
        for (int i = 0; i < MAX_FAILURES_PER_ADDRESS; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(from(clientIp))
                            .header("X-Forwarded-For", "9.9.9." + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest("admin@millikart.az", "WrongPass123!"))))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(from(clientIp))
                        .header("X-Forwarded-For", "9.9.9.250")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin@millikart.az", "WrongPass123!"))))
                .andExpect(status().isTooManyRequests());
    }

    // Свои опечатки вход снимает — общий офисный адрес не исчерпает попытки за утро; чужие логины остаются в
    // счёте. Для чужих отказов взяты несуществующие логины, чтобы блокировка аккаунта не мешала измерению.
    @Test
    @DisplayName("20. a successful login takes back only its own failures from the address")
    public void successfulLogin_clearsOnlyItsOwnFailuresFromTheAddress() throws Exception {
        String clientIp = "198.51.100.20";
        for (int i = 0; i < MAX_FAILURES_PER_ADDRESS - 2; i++) {
            login("nobody" + i + "@millikart.az", "WrongPass123!", clientIp).andExpect(status().isBadRequest());
        }
        login("admin@millikart.az", "WrongPass123!", clientIp).andExpect(status().isBadRequest());

        login("admin@millikart.az", ADMIN_PASSWORD, clientIp).andExpect(status().isOk());

        // RATE-LIMIT-RESET (Р-117): вход обнулял весь адрес, и свой вход каждые девять попыток прятал перебор
        // чужих логинов. Снимается только своя опечатка: две неудачи ещё проходят, третья — уже 429.
        login("nobody-a@millikart.az", "WrongPass123!", clientIp).andExpect(status().isBadRequest());
        login("nobody-b@millikart.az", "WrongPass123!", clientIp).andExpect(status().isBadRequest());
        login("nobody-c@millikart.az", "WrongPass123!", clientIp).andExpect(status().isTooManyRequests());
    }

    // Контексты тестов живут весь прогон: задача по расписанию сработала бы посреди чужого теста. Новый
    // планировщик без выключателя в тестовом yaml уронит этот тест.
    @Test
    public void noTaskRunsByTheClockInTests() {
        // Держатель задач есть всегда (@EnableScheduling): без него проверка прошла бы впустую.
        java.util.Collection<ScheduledTaskHolder> holders = applicationContext.getBeansOfType(ScheduledTaskHolder.class).values();
        org.junit.jupiter.api.Assertions.assertFalse(holders.isEmpty());
        List<String> tasks = holders.stream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .map(String::valueOf)
                .toList();
        Assertions.assertTrue(tasks.isEmpty(), "scheduled in tests: " + tasks);
    }

    // Фикстуры и хелперы

    private ResultActions login(String username, String password, String clientIp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(from(clientIp))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))));
    }

    // MockMvc по умолчанию сообщает 127.0.0.1, а это здесь доверенный прокси — задаём адрес явно.
    private static RequestPostProcessor from(String clientIp) {
        return request -> {
            request.setRemoteAddr(clientIp);
            return request;
        };
    }

    private void seedUser(String username, String password, String status) {
        userRepository.save(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Fixture User")
                .role("COMPANY_EMPLOYEE")
                .companyId("comp-01")
                .status(status)
                .build());
    }

    // Приводит аккаунт туда же, куда шесть неудачных попыток, не тратя шесть попыток.
    private void lockOut(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setFailedLoginAttempts(6);
        user.setLockoutUntil(Instant.now().plus(30, ChronoUnit.MINUTES));
        userRepository.save(user);
    }

    // Шесть неудач и блокировка, срок которой уже прошёл.
    private void expiredLockOut(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setFailedLoginAttempts(6);
        user.setLockoutUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
        userRepository.save(user);
    }

    private ObjectNode errorBody(ResultActions result) throws Exception {
        ObjectNode body = (ObjectNode) objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        // Метка времени различается между вызовами и ничего не говорит об аккаунте.
        body.remove("timestamp");
        body.put("httpStatus", result.andReturn().getResponse().getStatus());
        return body;
    }

    private static void assertSaysNothingAboutTheStatus(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        Assertions.assertFalse(body.toLowerCase().contains("blocked"),
                "the answer names the account status: " + body);
    }
}
