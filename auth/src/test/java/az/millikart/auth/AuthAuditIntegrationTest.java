package az.millikart.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.auth.domain.Company;
import az.millikart.auth.domain.User;
import az.millikart.auth.dto.CreateUserRequest;
import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.UserRepository;
import az.millikart.common.audit.AuditLog;
import az.millikart.common.audit.AuditOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// P2-14: auth пишет в журнал аудита — аккаунты и логины. Раньше создание администратора, смена
// роли и блокировка не оставляли следа нигде: журнал жил в directory и был недоступен отсюда.
// События логина нужны для PCI-DSS 10.2 и имеют собственные правила: пароль не должен попадать
// в details, а лимит по адресу не должен превращать журнал во флуд, от которого он и защищает.
@SpringBootTest
@AutoConfigureMockMvc
public class AuthAuditIntegrationTest {

    private static final String ADMIN = "admin@millikart.az";
    private static final String ADMIN_PASSWORD = "AdminPassword123!";
    private static final String USER_PASSWORD = "UserPassword123!";

    // auth.login.rate-limit.max-failures в тестовом профиле, как в production.
    private static final int MAX_FAILURES_PER_ADDRESS = 10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private AuditLogTestRepository auditLogs;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    public void setup() throws Exception {
        auditLogs.deleteAll();
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

        adminToken = "Bearer " + tokenOf(login(ADMIN, ADMIN_PASSWORD, "10.0.0.1"));
        auditLogs.deleteAll();
    }

    // 3-5. Аккаунты

    @Test
    public void creatingUser_isRecordedWithRoleAndClientIp() throws Exception {
        createUser("clerk@comp1.com", "COMPANY_EMPLOYEE");

        AuditLog record = single("CREATE");
        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(record.getEntityType()).isEqualTo("USER");
        assertThat(record.getPerformedBy()).isEqualTo(ADMIN);
        assertThat(record.getCompanyId()).isEqualTo("comp-01");
        assertThat(record.getDetails()).contains("clerk@comp1.com", "COMPANY_EMPLOYEE");
        assertThat(record.getClientIp()).isEqualTo("127.0.0.1");
    }

    // Смена роли — это смена привилегий, а "user updated" об этом не говорит ничего:
    // запись обязана нести оба конца перехода.
    @Test
    public void changingRole_recordsTheOldAndTheNewValue() throws Exception {
        UUID userId = createUser("clerk@comp1.com", "COMPANY_EMPLOYEE");
        auditLogs.deleteAll();

        mockMvc.perform(patch("/api/v1/users/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest(null, "SYSTEM_ADMIN", null, null))))
                .andExpect(status().isOk());

        assertThat(single("UPDATE").getDetails())
                .contains("role COMPANY_EMPLOYEE -> SYSTEM_ADMIN");
    }

    @Test
    public void blockingAndUnblockingAccount_areRecorded() throws Exception {
        UUID userId = createUser("clerk@comp1.com", "COMPANY_EMPLOYEE");
        auditLogs.deleteAll();

        updateStatus(userId, "BLOCKED");
        AuditLog blocked = single("BLOCK");
        assertThat(blocked.getEntityId()).isEqualTo(userId.toString());
        assertThat(blocked.getDetails()).contains("BLOCKED", "was ACTIVE");

        auditLogs.deleteAll();
        updateStatus(userId, "ACTIVE");
        assertThat(single("UNBLOCK").getDetails()).contains("reactivated");
    }

    @Test
    public void changingPassword_isRecordedWithoutThePassword() throws Exception {
        UUID userId = createUser("clerk@comp1.com", "COMPANY_EMPLOYEE");
        auditLogs.deleteAll();

        mockMvc.perform(patch("/api/v1/users/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest(null, null, "BrandNewSecret123!", null))))
                .andExpect(status().isOk());

        assertThat(single("PASSWORD_CHANGE").getDetails()).doesNotContain("BrandNewSecret123!");
        assertThat(everyDetail()).noneMatch(details -> details.contains("BrandNewSecret123!"));
    }

    // 6-9. Логины

    @Test
    public void successfulLogin_isRecordedAgainstTheUserWhoSignedIn() throws Exception {
        login(ADMIN, ADMIN_PASSWORD, "203.0.113.5").andExpect(status().isOk());

        AuditLog record = single("LOGIN");
        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(record.getPerformedBy()).isEqualTo(ADMIN);
        assertThat(record.getEntityType()).isEqualTo("AUTH");
        // Логин, а не UUID пользователя (P3-2): успех и отказы одного аккаунта должны попадать
        // под один фильтр по entityId, а у отказа UUID нет.
        assertThat(record.getEntityId()).isEqualTo(ADMIN);
    }

    // Введённого пароля не должно быть в журнале нигде: журнал читают не владельцы аккаунта,
    // а опечатка в пароле — часто настоящий пароль того же человека.
    @Test
    public void failedLogin_isRecordedAsDenied_withoutThePassword() throws Exception {
        login(ADMIN, "WrongPassword123!", "203.0.113.5").andExpect(status().isBadRequest());

        AuditLog record = single("LOGIN");
        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(record.getPerformedBy()).isEqualTo(ADMIN);
        assertThat(record.getDetails())
                .contains("wrong password")
                .doesNotContain("WrongPassword123!");
        assertThat(everyDetail()).noneMatch(details -> details.contains("WrongPassword123!"));
    }

    @Test
    public void failedLoginOfUnknownUser_isRecordedWithoutACompany() throws Exception {
        login("nobody@nowhere.com", "Whatever123!", "203.0.113.5").andExpect(status().isBadRequest());

        AuditLog record = single("LOGIN");
        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(record.getPerformedBy()).isEqualTo("nobody@nowhere.com");
        assertThat(record.getCompanyId()).isNull();
        assertThat(record.getDetails()).contains("no such account");
    }

    @Test
    public void accountLockout_isItsOwnRecord() throws Exception {
        // Шесть неверных паролей блокируют аккаунт (PCI-DSS 8.3.4). Каждая попытка идёт со своего
        // адреса, чтобы раньше не сработал лимит по адресу.
        for (int attempt = 1; attempt <= 6; attempt++) {
            login(ADMIN, "WrongPassword123!", "198.51.100." + attempt).andExpect(status().isBadRequest());
        }

        AuditLog lockout = single("LOCKOUT");
        assertThat(lockout.getOutcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(lockout.getPerformedBy()).isEqualTo(ADMIN);
        assertThat(lockout.getDetails()).contains("Account locked until");
    }

    // Logout (P3-2): конец сессии журналируется так же, как её начало.

    @Test
    public void logoutWithLiveToken_isRecordedAgainstTheLogin() throws Exception {
        String refreshToken = refreshTokenOf(login(ADMIN, ADMIN_PASSWORD, "203.0.113.5"));
        auditLogs.deleteAll();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        AuditLog record = single("LOGOUT");
        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(record.getEntityType()).isEqualTo("AUTH");
        assertThat(record.getEntityId()).isEqualTo(ADMIN);
        assertThat(record.getPerformedBy()).isEqualTo(ADMIN);
        assertThat(record.getDetails()).contains("revoked");
    }

    // Эндпоинт отвечает 204 на любой токен, знакомый или нет, чтобы им нельзя было прощупать,
    // какие токены существуют. Именно поэтому logout, ничего не погасивший, не журналируется:
    // случайную строку сюда шлёт кто угодно, и строка журнала на каждую сделала бы маскировку
    // каналом спама. Повторный logout того же токена — тот же случай: семья мертва, сессии нет.
    @Test
    public void logoutWithUnknownOrSpentToken_writesNothing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"definitely-not-a-token\"}"))
                .andExpect(status().isNoContent());
        assertThat(auditLogs.findAll()).isEmpty();

        String refreshToken = refreshTokenOf(login(ADMIN, ADMIN_PASSWORD, "203.0.113.5"));
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());
        auditLogs.deleteAll();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(auditLogs.findAll())
                .as("a repeat logout revokes nothing and must leave no record")
                .isEmpty();
    }

    // 10. Лимит по адресу пишет одну запись на окно, а не на каждую попытку.

    // Лимитер намеренно не ходит в базу — этим он и дёшев под флудом. Запись в журнал на каждую
    // отбитую попытку вернула бы флуду путь на запись и превратила защиту в усилитель. Отсюда:
    // одна запись в момент исчерпания адреса и ничего для попыток, отбитых после.
    @Test
    public void addressRateLimit_writesExactlyOneRecordPerWindow() throws Exception {
        String attacker = "198.51.100.77";
        for (int attempt = 1; attempt <= 20; attempt++) {
            login("nobody@nowhere.com", "Whatever123!", attacker);
        }

        List<AuditLog> rateLimitRecords = auditLogs.findAll().stream()
                .filter(record -> "RATE_LIMIT".equals(record.getAction()))
                .toList();
        assertThat(rateLimitRecords)
                .as("20 attempts past a limit of %d must leave one record, not one per attempt",
                        MAX_FAILURES_PER_ADDRESS)
                .hasSize(1);
        // entityId несёт логин, как у всех AUTH-записей (P3-2); исчерпавший попытки адрес
        // не теряется — он в колонке client_ip, где и был всегда.
        assertThat(rateLimitRecords.get(0).getEntityId()).isEqualTo("nobody@nowhere.com");
        assertThat(rateLimitRecords.get(0).getClientIp()).isEqualTo(attacker);

        // Сами отказы тоже не журналируются: пишутся лишь попытки, дошедшие до проверки пароля,
        // а они кончаются на лимите.
        assertThat(auditLogs.findAll().stream().filter(r -> "LOGIN".equals(r.getAction())).count())
                .isEqualTo(MAX_FAILURES_PER_ADDRESS);
    }

    // 12. Сбой журнала не должен стоить кому-то входа.

    // Таблица аудита удаляется на время попытки — это самое грубое "журнал сломан". Вход обязан
    // пройти: запись в аудит, способная отказать во входе, — это рубильник отказа в обслуживании.
    @Test
    public void auditFailure_doesNotBreakLogin() throws Exception {
        auditLogs.deleteAll();
        try {
            dropAuditTable();

            login(ADMIN, ADMIN_PASSWORD, "203.0.113.9").andExpect(status().isOk());
            login(ADMIN, "WrongPassword123!", "203.0.113.9").andExpect(status().isBadRequest());
        } finally {
            restoreAuditTable();
        }
    }

    // Фикстуры

    private UUID createUser(String username, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateUserRequest(username, USER_PASSWORD, "Test User", role, "comp-01"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private void updateStatus(UUID userId, String status) throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest(null, null, null, status))))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password,
                                                                     String clientIp) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password)));
        request.with(servletRequest -> {
            servletRequest.setRemoteAddr(clientIp);
            return servletRequest;
        });
        return mockMvc.perform(request);
    }

    private String tokenOf(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        String body = actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String refreshTokenOf(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        String body = actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("refreshToken").asText();
    }

    // Ровно одна запись с этим action; она и возвращается.
    private AuditLog single(String action) {
        List<AuditLog> records = auditLogs.findAll().stream()
                .filter(record -> action.equals(record.getAction()))
                .toList();
        assertThat(records).as("expected exactly one %s record, got %s", action, records.size()).hasSize(1);
        return records.get(0);
    }

    private List<String> everyDetail() {
        return auditLogs.findAll().stream().map(AuditLog::getDetails).filter(d -> d != null).toList();
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private void dropAuditTable() {
        jdbcTemplate.execute("DROP TABLE audit_logs");
    }

    private void restoreAuditTable() {
        jdbcTemplate.execute("""
                CREATE TABLE audit_logs (
                    id uuid NOT NULL,
                    entity_type varchar(50) NOT NULL,
                    entity_id varchar(255) NOT NULL,
                    action varchar(50) NOT NULL,
                    performed_by varchar(255) NOT NULL,
                    company_id varchar(255),
                    details varchar(4000),
                    client_ip varchar(45),
                    outcome varchar(16) DEFAULT 'SUCCESS' NOT NULL,
                    created_at timestamp,
                    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
                )""");
    }
}
