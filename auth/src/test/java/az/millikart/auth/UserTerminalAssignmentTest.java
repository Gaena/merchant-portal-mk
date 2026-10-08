package az.millikart.auth;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.auth.domain.Company;
import az.millikart.auth.domain.User;
import az.millikart.auth.dto.CreateUserRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.UserRepository;
import az.millikart.common.security.JwtProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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

// Р-131: сотрудник видит только назначенные терминалы, поэтому заводится только с ними, и только своей
// компании; другим ролям терминалы не назначаются. Правка заменяет список целиком и пишется в журнал;
// смена роли и компании подчиняется тем же правилам, а сотрудника, заведённого до назначений, можно
// править, не раздавая ему терминалы. Токены — подписанные напрямую: вход здесь не предмет теста.
@SpringBootTest
@AutoConfigureMockMvc
class UserTerminalAssignmentTest {

    private static final String PASSWORD = "UserPassword123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    private int first;
    private int second;
    private int foreign;
    private String adminToken;
    private String headToken;

    @BeforeEach
    void setUp() {
        TerminalFixture.ensureSchema(dataSource);
        TerminalFixture.clean(jdbcTemplate);
        userRepository.deleteAll();
        companyRepository.deleteAll();
        companyRepository.save(Company.builder().id("comp-01").name("MilliKart LLC").status("ACTIVE").build());
        companyRepository.save(Company.builder().id("comp-02").name("Other LLC").status("ACTIVE").build());
        first = TerminalFixture.terminalOf(jdbcTemplate, "comp-01");
        second = TerminalFixture.extraTerminalOf(jdbcTemplate, "comp-01", 1);
        foreign = TerminalFixture.terminalOf(jdbcTemplate, "comp-02");
        adminToken = token("admin@millikart.az", "SYSTEM_ADMIN", null);
        headToken = token("head@comp01.com", "COMPANY_HEAD", "comp-01");
    }

    @AfterEach
    void tearDown() {
        TerminalFixture.clean(jdbcTemplate);
    }

    // Сотрудник без терминала видел бы пустой портал, с чужим — данные чужой компании; менеджеру
    // назначение ничего бы не дало и только путало. Ни один из отказов не заводит учётку.
    @Test
    void employee_needsAtLeastOneTerminalOfItsCompany_andOnlyEmployeesGetTerminals() throws Exception {
        create(headToken, "noterm@comp01.com", "COMPANY_EMPLOYEE", "comp-01", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("An employee needs at least one terminal")));
        create(headToken, "noterm@comp01.com", "COMPANY_EMPLOYEE", "comp-01", List.of())
                .andExpect(status().isBadRequest());
        create(adminToken, "foreign@comp01.com", "COMPANY_EMPLOYEE", "comp-01", List.of(first, foreign))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Terminals [" + foreign + "] do not belong to company comp-01")));
        create(headToken, "manager@comp01.com", "COMPANY_MANAGER", "comp-01", List.of(first))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Terminals are assigned to employees only")));

        Assertions.assertEquals(0, userRepository.count());
    }

    // Список без повторов и по возрастанию — в ответе, в одиночном чтении и в списке пользователей; правка
    // заменяет его целиком, пишет «с чего на что» и пустым списком сотрудника не оставит. Правка без
    // терминалов их не трогает.
    @Test
    void head_createsAnEmployee_andReplacesItsTerminals() throws Exception {
        UUID id = idOf(create(headToken, "clerk@comp01.com", "COMPANY_EMPLOYEE", "comp-01", List.of(second, first, first))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.terminalIds", contains(first, second))));

        mockMvc.perform(get("/api/v1/users/" + id).header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminalIds", contains(first, second)));
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].terminalIds", contains(first, second)));

        update(headToken, id, new UpdateUserRequest(null, null, null, null, null, List.of(second), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminalIds", contains(second)));
        Assertions.assertEquals("Changed terminals [" + first + ", " + second + "] -> [" + second + "]",
                lastUpdateDetails(id));

        update(headToken, id, new UpdateUserRequest(null, null, null, null, null, List.of(), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("An employee needs at least one terminal")));
        update(headToken, id, new UpdateUserRequest("Renamed", null, null, null, null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminalIds", contains(second)));
    }

    // Сотрудники, заведённые до назначений, при выкладке терминалов не получают; руководитель обязан
    // мочь их переименовать и заблокировать, не раздавая терминалы, — иначе правка любого поля упиралась
    // бы в отказ.
    @Test
    void employeeWithoutTerminals_canStillBeRenamedAndBlocked() throws Exception {
        User legacy = userRepository.save(User.builder()
                .username("legacy@comp01.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Legacy")
                .role("COMPANY_EMPLOYEE")
                .companyId("comp-01")
                .status("ACTIVE")
                .build());

        update(headToken, legacy.getId(), new UpdateUserRequest("Legacy Renamed", null, null, "BLOCKED", null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminalIds", empty()));
    }

    // Ставший менеджером назначения теряет; ставший сотрудником — только с терминалами; перевод сотрудника в
    // другую компанию — только с её терминалами: прежние ей не принадлежат.
    @Test
    void roleAndCompanyChanges_followTheTerminalRules() throws Exception {
        UUID id = idOf(create(adminToken, "clerk@comp01.com", "COMPANY_EMPLOYEE", "comp-01", List.of(first))
                .andExpect(status().isCreated()));

        update(adminToken, id, new UpdateUserRequest(null, "COMPANY_MANAGER", null, null, null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminalIds", empty()));
        Assertions.assertEquals(0, assignmentsOf(id));

        update(adminToken, id, new UpdateUserRequest(null, "COMPANY_EMPLOYEE", null, null, null, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("An employee needs at least one terminal")));
        update(adminToken, id, new UpdateUserRequest(null, "COMPANY_EMPLOYEE", null, null, null, List.of(first), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminalIds", contains(first)));

        update(adminToken, id, new UpdateUserRequest(null, null, null, null, "comp-02", null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("An employee needs at least one terminal")));
        Assertions.assertEquals("comp-01", userRepository.findById(id).orElseThrow().getCompanyId(),
                "the refused move is rolled back as a whole");
        update(adminToken, id, new UpdateUserRequest(null, null, null, null, "comp-02", List.of(foreign), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId", is("comp-02")))
                .andExpect(jsonPath("$.terminalIds", contains(foreign)));
    }

    // Живой access-токен удалённого сотрудника доживает свои 15 минут — без назначений он не видит ничего.
    @Test
    void deletingAnEmployee_dropsItsTerminals() throws Exception {
        UUID id = idOf(create(headToken, "clerk@comp01.com", "COMPANY_EMPLOYEE", "comp-01", List.of(first))
                .andExpect(status().isCreated()));

        mockMvc.perform(delete("/api/v1/users/" + id).header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isNoContent());

        Assertions.assertEquals(0, assignmentsOf(id));
    }

    private ResultActions create(String token, String username, String role, String companyId,
                                 List<Integer> terminalIds) throws Exception {
        return mockMvc.perform(post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateUserRequest(username, PASSWORD, "Test User", role, companyId, terminalIds, null))));
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

    private int assignmentsOf(UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_terminals WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private String lastUpdateDetails(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT details FROM audit_logs WHERE entity_id = ? AND action = 'UPDATE' ORDER BY created_at DESC LIMIT 1",
                String.class, userId.toString());
    }

    private String token(String username, String role, String companyId) {
        return "Bearer " + jwtProvider.generateToken(UUID.randomUUID().toString(), username, role, companyId);
    }
}
