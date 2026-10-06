package az.millikart.auth;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.auth.domain.Company;
import az.millikart.auth.domain.RefreshToken;
import az.millikart.auth.domain.User;
import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.dto.RefreshRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.RefreshTokenRepository;
import az.millikart.auth.repository.UserRepository;
import az.millikart.auth.service.InactiveAccountService;
import az.millikart.common.audit.AuditLog;
import az.millikart.common.audit.AuditOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

// PCI DSS 8.2.6 (Р-101): учётка без активности дольше 90 дней блокируется. Цена ошибки в обе стороны:
// забытая учётка уволенного сотрудника остаётся входом в систему, а заблокированный работающий человек —
// звонок в поддержку. Сервис зовётся со своими часами: планировщик в тестовом профиле выключен.
@SpringBootTest
@AutoConfigureMockMvc
class InactiveAccountIntegrationTest {

    // Своя сеть (TEST-NET-1): 198.51.100.x и 203.0.113.x заняты тестами, которые нарочно исчерпывают лимит.
    private static final String CLIENT_ADDRESS = "192.0.2.101";
    private static final Duration MAX_IDLE = Duration.ofDays(90);
    private static final String ADMIN = "admin@millikart.az";
    private static final String CLERK = "clerk@comp01.com";
    private static final String PASSWORD = "ClerkPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private InactiveAccountService inactiveAccounts;

    @Autowired
    private AuditLogTestRepository auditLogs;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
        companyRepository.save(Company.builder().id("comp-01").name("MilliKart LLC").status("ACTIVE").build());
        seed(ADMIN, "SYSTEM_ADMIN", null);
        seed(CLERK, "COMPANY_EMPLOYEE", "comp-01");
    }

    @Test
    void anAccountIdleForLongerThan90Days_isBlocked_andItsSessionsEnd() throws Exception {
        String refreshToken = objectMapper.readTree(login(CLERK).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("refreshToken").asText();
        Instant lastActivity = Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.SECONDS);
        age(CLERK, lastActivity);
        age(ADMIN, lastActivity);

        // Ровно на границе — ещё не простой: блокируются только те, кто старше порога.
        assertEquals(0, inactiveAccounts.blockInactive(lastActivity.plus(MAX_IDLE)));
        assertEquals("ACTIVE", user(CLERK).getStatus());

        // Администратор не исключение — и он простаивал столько же.
        assertEquals(2, inactiveAccounts.blockInactive(lastActivity.plus(MAX_IDLE).plusSeconds(60)));
        assertEquals("BLOCKED", user(CLERK).getStatus());
        assertEquals("BLOCKED", user(ADMIN).getStatus());
        assertTrue(refreshTokenRepository.findAllByUserId(user(CLERK).getId()).stream().allMatch(RefreshToken::isRevoked));
        mockMvc.perform(fromClient(post("/api/v1/auth/refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
        login(CLERK).andExpect(status().isBadRequest());
    }

    // Блокирует планировщик, а не человек: актор — system (P3-2), причина и последняя активность — в записи,
    // иначе администратор не отличит автоблокировку от ручной.
    @Test
    void idleBlocking_isRecordedAgainstTheSystem() {
        Instant lastActivity = Instant.now().minus(MAX_IDLE).minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.SECONDS);
        age(CLERK, lastActivity);

        inactiveAccounts.blockInactive(Instant.now());

        String clerkId = user(CLERK).getId().toString();
        List<AuditLog> records = auditLogs.findAll().stream()
                .filter(record -> clerkId.equals(record.getEntityId()))
                .toList();
        assertEquals(1, records.size(), String.valueOf(records));
        AuditLog record = records.getFirst();
        assertEquals("BLOCK", record.getAction());
        assertEquals(AuditOutcome.SUCCESS, record.getOutcome());
        assertEquals("system", record.getPerformedBy());
        assertEquals("comp-01", record.getCompanyId());
        assertTrue(record.getDetails().contains("PCI DSS 8.2.6") && record.getDetails().contains(lastActivity.toString()),
                record.getDetails());
    }

    // Вход — новый отсчёт; учётки, уже не ACTIVE, проход не трогает.
    @Test
    void aSignIn_restartsTheClock_andBlockedOrDeletedAccountsAreLeftAlone() throws Exception {
        Instant old = Instant.now().minus(MAX_IDLE).minus(Duration.ofDays(10));
        age(CLERK, old);
        age(ADMIN, old);
        User deleted = seed("gone@comp01.com", "COMPANY_EMPLOYEE", "comp-01");
        deleted.setStatus("DELETED");
        deleted.setLastActivityAt(old);
        userRepository.save(deleted);

        login(CLERK).andExpect(status().isOk());

        assertEquals(1, inactiveAccounts.blockInactive(Instant.now()), "only the admin, who did not sign in");
        assertEquals("ACTIVE", user(CLERK).getStatus());
        assertEquals("BLOCKED", user(ADMIN).getStatus());
        assertEquals("DELETED", user("gone@comp01.com").getStatus());
    }

    // Разблокировка — тоже новый отсчёт: иначе ближайший проход снова заблокировал бы учётку.
    @Test
    void unblocking_restartsTheClock() throws Exception {
        age(CLERK, Instant.now().minus(MAX_IDLE).minus(Duration.ofDays(1)));
        inactiveAccounts.blockInactive(Instant.now());
        assertEquals("BLOCKED", user(CLERK).getStatus());

        String adminToken = "Bearer " + objectMapper.readTree(login(ADMIN).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        mockMvc.perform(patch("/api/v1/users/" + user(CLERK).getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRequest(null, null, null, "ACTIVE", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        assertEquals(0, inactiveAccounts.blockInactive(Instant.now()));
        assertEquals("ACTIVE", user(CLERK).getStatus());
    }

    // Работа без нового входа — тоже активность, но писать её чаще раза в сутки незачем.
    @Test
    void aSessionRefresh_countsAsActivity_atMostOnceADay() throws Exception {
        String refreshToken = objectMapper.readTree(login(CLERK).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("refreshToken").asText();
        Instant twoDaysAgo = Instant.now().minus(Duration.ofDays(2));
        age(CLERK, twoDaysAgo);

        String next = refresh(refreshToken);
        Instant bumped = user(CLERK).getLastActivityAt();
        assertTrue(bumped.isAfter(twoDaysAgo.plus(Duration.ofDays(1))), "a refresh after a day of quiet is activity");

        refresh(next);
        assertEquals(bumped, user(CLERK).getLastActivityAt(), "a second refresh the same day writes nothing");
    }

    private User seed(String username, String role, String companyId) {
        return userRepository.save(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Fixture")
                .role(role)
                .companyId(companyId)
                .status("ACTIVE")
                .build());
    }

    private void age(String username, Instant lastActivity) {
        User user = user(username);
        user.setLastActivityAt(lastActivity);
        userRepository.save(user);
    }

    private User user(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    private ResultActions login(String username) throws Exception {
        return mockMvc.perform(fromClient(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, PASSWORD))));
    }

    private String refresh(String refreshToken) throws Exception {
        String body = mockMvc.perform(fromClient(post("/api/v1/auth/refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("refreshToken").asText();
    }

    private static MockHttpServletRequestBuilder fromClient(MockHttpServletRequestBuilder request) {
        return request.with(servletRequest -> {
            servletRequest.setRemoteAddr(CLIENT_ADDRESS);
            return servletRequest;
        });
    }
}
