package az.millikart.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.auth.domain.Company;
import az.millikart.auth.domain.RefreshToken;
import az.millikart.auth.domain.User;
import az.millikart.auth.dto.CreateUserRequest;
import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.dto.RefreshRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.RefreshTokenRepository;
import az.millikart.auth.repository.UserRepository;
import az.millikart.common.audit.AuditLog;
import az.millikart.common.audit.AuditOutcome;
import az.millikart.common.security.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtProvider jwtProvider;

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
                                new UpdateUserRequest(null, "SYSTEM_ADMIN", null, null, null, null, null))))
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
                                new UpdateUserRequest(null, null, "BrandNewSecret123!", null, null, null, null))))
                .andExpect(status().isOk());

        assertThat(single("PASSWORD_CHANGE").getDetails()).doesNotContain("BrandNewSecret123!");
        assertThat(everyDetail()).noneMatch(details -> details.contains("BrandNewSecret123!"));
    }

    @Test
    public void deletingUser_isRecordedWithTheLoginAndRoleOfTheDeleted() throws Exception {
        UUID userId = createUser("clerk@comp1.com", "COMPANY_EMPLOYEE");
        auditLogs.deleteAll();

        mockMvc.perform(delete("/api/v1/users/" + userId).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());

        AuditLog record = single("DELETE");
        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(record.getEntityType()).isEqualTo("USER");
        assertThat(record.getEntityId()).isEqualTo(userId.toString());
        assertThat(record.getPerformedBy()).isEqualTo(ADMIN);
        assertThat(record.getCompanyId()).isEqualTo("comp-01");
        assertThat(record.getDetails()).contains("clerk@comp1.com", "COMPANY_EMPLOYEE");
    }

    // Отказы USER из словаря (technical_handover §4.4): руководитель выдаёт роль выше своей, правит и удаляет
    // руководителя той же компании. companyId отказа — компания актора, а не названная в запросе.
    @Test
    public void headOverstepping_isRecordedAsDenied() throws Exception {
        UUID otherHead = createUser("second.head@comp1.com", "COMPANY_HEAD");
        String headToken = "Bearer " + jwtProvider.generateToken(
                UUID.randomUUID().toString(), "head@comp1.com", "COMPANY_HEAD", "comp-01");
        auditLogs.deleteAll();

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateUserRequest("boss@comp1.com", USER_PASSWORD, "Boss", "SYSTEM_ADMIN", "comp-01", null, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/users/" + otherHead)
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRequest("Renamed", null, null, null, null, null, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/users/" + otherHead).header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isForbidden());

        AuditLog create = single("CREATE");
        assertThat(create.getEntityId()).isEqualTo("boss@comp1.com");
        assertThat(create.getDetails()).contains("attempted to create a user with role SYSTEM_ADMIN");
        assertThat(single("UPDATE").getDetails()).contains("attempted UPDATE of user " + otherHead + " with role COMPANY_HEAD");
        assertThat(single("DELETE").getDetails()).contains("attempted DELETE of user " + otherHead + " with role COMPANY_HEAD");
        assertThat(auditLogs.findAll()).allSatisfy(record -> {
            assertThat(record.getOutcome()).isEqualTo(AuditOutcome.DENIED);
            assertThat(record.getPerformedBy()).isEqualTo("head@comp1.com");
            assertThat(record.getCompanyId()).isEqualTo("comp-01");
        });
    }

    // Роль без права на пользователей: отказ в заведении и в списке — записи, как у компаний и терминалов.
    @Test
    public void aRoleWithoutUserRights_isRecordedAsDenied() throws Exception {
        String employeeToken = "Bearer " + jwtProvider.generateToken(
                UUID.randomUUID().toString(), "clerk@comp1.com", "COMPANY_EMPLOYEE", "comp-01");

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateUserRequest("friend@comp1.com", USER_PASSWORD, "Friend", "COMPANY_EMPLOYEE", "comp-01", null, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, employeeToken))
                .andExpect(status().isForbidden());

        AuditLog create = single("CREATE");
        assertThat(create.getEntityId()).isEqualTo("friend@comp1.com");
        assertThat(create.getDetails()).isEqualTo("Denied: role COMPANY_EMPLOYEE attempted to create a user");
        AuditLog list = single("LIST");
        assertThat(list.getEntityId()).isEqualTo("ALL");
        assertThat(list.getDetails()).isEqualTo("Denied: role COMPANY_EMPLOYEE attempted to list users");
        assertThat(auditLogs.findAll()).allSatisfy(record -> {
            assertThat(record.getOutcome()).isEqualTo(AuditOutcome.DENIED);
            assertThat(record.getPerformedBy()).isEqualTo("clerk@comp1.com");
            assertThat(record.getCompanyId()).isEqualTo("comp-01");
        });
    }

    // Руководитель чужой компании: заведение в неё, чтение, правка и удаление её пользователя. Компания цели в
    // записи не называется — руководитель читает журнал своей компании и узнал бы, чей это UUID.
    @Test
    public void aHeadReachingIntoAnotherCompany_isRecordedAsDenied() throws Exception {
        UUID clerk = createUser("clerk@comp1.com", "COMPANY_EMPLOYEE");
        String foreignHead = "Bearer " + jwtProvider.generateToken(
                UUID.randomUUID().toString(), "head@comp2.com", "COMPANY_HEAD", "comp-02");
        auditLogs.deleteAll();

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, foreignHead)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateUserRequest("mole@comp1.com", USER_PASSWORD, "Mole", "COMPANY_EMPLOYEE", "comp-01", null, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/" + clerk).header(HttpHeaders.AUTHORIZATION, foreignHead))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/users/" + clerk)
                        .header(HttpHeaders.AUTHORIZATION, foreignHead)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRequest(null, null, null, "BLOCKED", null, null, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/users/" + clerk).header(HttpHeaders.AUTHORIZATION, foreignHead))
                .andExpect(status().isForbidden());

        assertThat(single("CREATE").getDetails())
                .isEqualTo("Denied: role COMPANY_HEAD attempted to create a user in company comp-01");
        for (String action : List.of("READ", "UPDATE", "DELETE")) {
            AuditLog record = single(action);
            assertThat(record.getEntityId()).isEqualTo(clerk.toString());
            assertThat(record.getDetails())
                    .isEqualTo("Denied: role COMPANY_HEAD of company comp-02 attempted " + action
                            + " of user " + clerk + " outside its company");
        }
        assertThat(auditLogs.findAll()).allSatisfy(record -> {
            assertThat(record.getOutcome()).isEqualTo(AuditOutcome.DENIED);
            assertThat(record.getPerformedBy()).isEqualTo("head@comp2.com");
            assertThat(record.getCompanyId()).isEqualTo("comp-02");
        });
    }

    // Access-токен живёт после блокировки до 15 минут; отказ заблокированному актору — тоже запись.
    @Test
    public void aBlockedActorWithALiveToken_isRecordedAsDenied() throws Exception {
        UUID target = createUser("clerk@comp1.com", "COMPANY_EMPLOYEE");
        User blocked = userRepository.save(User.builder()
                .username("blocked.admin@millikart.az")
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .fullName("Blocked Admin")
                .role("SYSTEM_ADMIN")
                .status("BLOCKED")
                .build());
        String blockedToken = "Bearer " + jwtProvider.generateToken(
                blocked.getId().toString(), blocked.getUsername(), "SYSTEM_ADMIN", null);
        auditLogs.deleteAll();

        mockMvc.perform(patch("/api/v1/users/" + target)
                        .header(HttpHeaders.AUTHORIZATION, blockedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRequest(null, null, null, "BLOCKED", null, null, null))))
                .andExpect(status().isForbidden());

        AuditLog record = single("UPDATE");
        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(record.getEntityId()).isEqualTo(target.toString());
        assertThat(record.getPerformedBy()).isEqualTo("blocked.admin@millikart.az");
        assertThat(record.getDetails()).isEqualTo("Denied: actor account is BLOCKED");
    }

    // 6-9. Логины
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

    // Повтор ротированного refresh-токена вне окна снисхождения — признак кражи (P1-12): семья гасится,
    // запись несёт логин владельца и ни токена, ни его части.
    @Test
    public void reusingARotatedRefreshToken_isRecordedWithoutTheToken() throws Exception {
        String stolen = refreshTokenOf(login(ADMIN, ADMIN_PASSWORD, "203.0.113.5"));
        refresh(stolen).andExpect(status().isOk());
        // Ротация «час назад»: окно (10 с) прошло без sleep.
        RefreshToken rotated = refreshTokenRepository.findAll().stream()
                .filter(RefreshToken::isRotated)
                .findFirst().orElseThrow();
        rotated.setRotatedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        refreshTokenRepository.save(rotated);
        auditLogs.deleteAll();

        refresh(stolen).andExpect(status().isUnauthorized());

        AuditLog record = single("TOKEN_REUSE");
        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(record.getEntityType()).isEqualTo("AUTH");
        assertThat(record.getEntityId()).isEqualTo(ADMIN);
        assertThat(record.getPerformedBy()).isEqualTo(ADMIN);
        assertThat(record.getDetails()).contains("reused", "revoked").doesNotContain(stolen);
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
        assertThat(rateLimitRecords.getFirst().getEntityId()).isEqualTo("nobody@nowhere.com");
        assertThat(rateLimitRecords.getFirst().getClientIp()).isEqualTo(attacker);

        // Сами отказы тоже не журналируются: пишутся лишь попытки, дошедшие до проверки пароля,
        // а они кончаются на лимите.
        assertThat(auditLogs.findAll().stream().filter(r -> "LOGIN".equals(r.getAction())).count())
                .isEqualTo(MAX_FAILURES_PER_ADDRESS);
    }

    // 12. Сбой журнала не должен стоить кому-то входа.

    // Таблицы аудита нет на время попытки — это самое грубое "журнал сломан". Вход обязан
    // пройти: запись в аудит, способная отказать во входе, — это рубильник отказа в обслуживании.
    @Test
    public void auditFailure_doesNotBreakLogin() throws Exception {
        auditLogs.deleteAll();
        try {
            parkAuditTable();

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
                                new CreateUserRequest(username, USER_PASSWORD, "Test User", role, "comp-01",
                                        "COMPANY_EMPLOYEE".equals(role)
                                                ? List.of(TerminalFixture.terminalOf(jdbcTemplate, "comp-01")) : null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private void updateStatus(UUID userId, String status) throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest(null, null, null, status, null, null, null))))
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

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))));
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
        return records.getFirst();
    }

    private List<String> everyDetail() {
        return auditLogs.findAll().stream().map(AuditLog::getDetails).filter(d -> d != null).toList();
    }

    // Журнал ломается переименованием, а не DROP: база общая на все классы модуля, и таблица должна
    // вернуться ровно той, что была, — с индексами и умолчаниями, а не рукописной копией.
    private void parkAuditTable() {
        jdbcTemplate.execute("ALTER TABLE audit_logs RENAME TO audit_logs_parked");
    }

    private void restoreAuditTable() {
        jdbcTemplate.execute("ALTER TABLE audit_logs_parked RENAME TO audit_logs");
    }
}
