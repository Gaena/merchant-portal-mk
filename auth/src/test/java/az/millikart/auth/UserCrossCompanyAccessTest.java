package az.millikart.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.is;

import az.millikart.auth.domain.Company;
import az.millikart.auth.domain.User;
import az.millikart.auth.dto.CreateUserRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.UserRepository;
import az.millikart.common.security.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Отказы между компаниями и по ролям в /users. Успешные сценарии покрыты в AuthIntegrationTest; здесь —
// то, что ловит ослабление проверки в одной ветке UserService: чужой руководитель, повышение роли,
// роли без права записи, заблокированный актор с живым токеном. Токены — подписанные напрямую: вход
// тут не предмет теста, а строки актора в базе нужны только там, где проверяется его статус.
@SpringBootTest
@AutoConfigureMockMvc
class UserCrossCompanyAccessTest {

    private static final String PASSWORD = "UserPassword123!";

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

    @Autowired
    private JwtProvider jwtProvider;

    private User employee;
    private User otherHead;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        companyRepository.deleteAll();
        companyRepository.save(Company.builder().id("comp-01").name("MilliKart LLC").status("ACTIVE").build());
        companyRepository.save(Company.builder().id("comp-02").name("Other LLC").status("ACTIVE").build());

        employee = user("employee@comp01.com", "COMPANY_EMPLOYEE", "comp-01", "ACTIVE");
        otherHead = user("head2@comp01.com", "COMPANY_HEAD", "comp-01", "ACTIVE");
    }

    // Руководитель чужой компании не видит и не трогает пользователя: ни чтения, ни правки (в том числе
    // пароля — это вход под чужой учёткой), ни удаления. Ловит сравнение компаний, пропускающее чужую.
    @Test
    void headOfAnotherCompany_cannotReadEditOrDeleteAUser() throws Exception {
        String foreignHead = token(UUID.randomUUID(), "head@comp02.com", "COMPANY_HEAD", "comp-02");
        String hashBefore = employee.getPasswordHash();

        mockMvc.perform(get("/api/v1/users/" + employee.getId()).header(HttpHeaders.AUTHORIZATION, foreignHead))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Access denied")));
        mockMvc.perform(patchUser(employee.getId(), foreignHead, new UpdateUserRequest("Renamed", null, null, null, null, null, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patchUser(employee.getId(), foreignHead, new UpdateUserRequest(null, null, "Takeover12345!", null, null, null, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/users/" + employee.getId()).header(HttpHeaders.AUTHORIZATION, foreignHead))
                .andExpect(status().isForbidden());

        User after = userRepository.findById(employee.getId()).orElseThrow();
        Assertions.assertEquals("Test User", after.getFullName());
        Assertions.assertEquals(hashBefore, after.getPasswordHash());
        Assertions.assertEquals("ACTIVE", after.getStatus());
    }

    // Руководитель своей компании не повышает подчинённого до руководителя и не трогает равного
    // руководителя — ни правкой, ни удалением. Ловит HEAD_MANAGED_ROLES, расширенный до COMPANY_HEAD.
    @Test
    void head_cannotRaiseARole_orEditOrDeleteAnotherHead() throws Exception {
        User head = user("head@comp01.com", "COMPANY_HEAD", "comp-01", "ACTIVE");
        String headToken = token(head.getId(), head.getUsername(), "COMPANY_HEAD", "comp-01");

        mockMvc.perform(patchUser(employee.getId(), headToken, new UpdateUserRequest(null, "COMPANY_HEAD", null, null, null, null, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Cannot assign this role")));
        mockMvc.perform(patchUser(otherHead.getId(), headToken, new UpdateUserRequest("Renamed", null, null, null, null, null, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Access denied")));
        mockMvc.perform(delete("/api/v1/users/" + otherHead.getId()).header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isForbidden());

        Assertions.assertEquals("COMPANY_EMPLOYEE", userRepository.findById(employee.getId()).orElseThrow().getRole());
        User headAfter = userRepository.findById(otherHead.getId()).orElseThrow();
        Assertions.assertEquals("Test User", headAfter.getFullName());
        Assertions.assertEquals("ACTIVE", headAfter.getStatus());
    }

    // В /users пишут только администратор и руководитель. Менеджер, сотрудник, аудитор (читатель всех
    // компаний, Р-1) и нераспознанная роль получают 403 и на свою компанию. Ловит проверку, открытую для
    // «любой роли с companyId».
    @Test
    void rolesWithoutWriteAccess_cannotCreateEditOrDeleteUsers() throws Exception {
        for (String role : List.of("COMPANY_MANAGER", "COMPANY_EMPLOYEE", "AUDITOR", "WIZARD")) {
            String actor = token(UUID.randomUUID(), role.toLowerCase() + "@comp01.com", role, "comp-01");

            mockMvc.perform(post("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, actor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreateUserRequest(
                                    "new-" + role.toLowerCase() + "@comp01.com", PASSWORD, "New User",
                                    "COMPANY_EMPLOYEE", "comp-01", null, null))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patchUser(employee.getId(), actor, new UpdateUserRequest("Renamed", null, null, null, null, null, null)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/users/" + employee.getId()).header(HttpHeaders.AUTHORIZATION, actor))
                    .andExpect(status().isForbidden());

            Assertions.assertTrue(userRepository.findByUsername("new-" + role.toLowerCase() + "@comp01.com").isEmpty(),
                    role + " must not create users");
        }
        User after = userRepository.findById(employee.getId()).orElseThrow();
        Assertions.assertEquals("Test User", after.getFullName());
        Assertions.assertEquals("ACTIVE", after.getStatus());
    }

    // Access-токен живёт до 15 минут после блокировки: заблокированный руководитель с живым токеном не
    // заводит и не удаляет пользователей (правку проверяет AuthIntegrationTest). Ловит requireActiveActor,
    // оставшийся только в updateUser.
    @Test
    void blockedHeadWithALiveToken_cannotCreateOrDeleteUsers() throws Exception {
        User blocked = user("blocked-head@comp01.com", "COMPANY_HEAD", "comp-01", "BLOCKED");
        String liveToken = token(blocked.getId(), blocked.getUsername(), "COMPANY_HEAD", "comp-01");

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, liveToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(
                                "late@comp01.com", PASSWORD, "Late User", "COMPANY_EMPLOYEE", "comp-01", null, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/users/" + employee.getId()).header(HttpHeaders.AUTHORIZATION, liveToken))
                .andExpect(status().isForbidden());

        Assertions.assertTrue(userRepository.findByUsername("late@comp01.com").isEmpty());
        Assertions.assertEquals("ACTIVE", userRepository.findById(employee.getId()).orElseThrow().getStatus());
    }

    private User user(String username, String role, String companyId, String status) {
        return userRepository.save(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Test User")
                .role(role)
                .companyId(companyId)
                .status(status)
                .build());
    }

    private String token(UUID userId, String username, String role, String companyId) {
        return "Bearer " + jwtProvider.generateToken(userId.toString(), username, role, companyId);
    }

    private MockHttpServletRequestBuilder patchUser(UUID id, String token, UpdateUserRequest body) throws Exception {
        return patch("/api/v1/users/" + id)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }
}
