package az.millikart.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.auth.domain.Company;
import az.millikart.auth.domain.User;
import az.millikart.auth.dto.ChangePasswordRequest;
import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.dto.MfaSetupRequest;
import az.millikart.auth.dto.MfaVerifyRequest;
import az.millikart.auth.dto.RefreshRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.UserRepository;
import az.millikart.auth.security.Totp;
import az.millikart.common.audit.AuditLog;
import az.millikart.common.audit.AuditOutcome;
import az.millikart.common.security.JwtProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// PCI DSS 8.4.2, 8.5.1 (Р-141): после верного пароля сессии нет без кода второго фактора. Цена ошибки — вход по
// одному утёкшему паролю, перебор кодов или повтор подсмотренного кода. Свой контекст: в остальных тестах фактор
// выключен (тестовый yaml). Запросы — со своего адреса, общий лимит 127.0.0.1 другим тестам нужен целым.
@SpringBootTest(properties = "auth.mfa.enabled=true")
@AutoConfigureMockMvc
class MfaIntegrationTest {

    // Свой адрес на тест (TEST-NET-1, с .120; .100 и .101 заняты другими классами): неудачи одного теста не
    // упираются в лимит адреса у другого.
    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger(120);
    private static final String ADMIN = "admin@millikart.az";
    private static final String HEAD = "head@comp01.com";
    private static final String MANAGER = "manager@comp01.com";
    private static final String PASSWORD = "Password123!";
    private static final String INVALID_STEP = "Sign-in step is invalid or expired. Sign in again.";

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditLogTestRepository auditLogs;

    private String clientAddress;

    private record Enrolled(String secret, JsonNode session) {
    }

    @BeforeEach
    void setUp() {
        clientAddress = "192.0.2." + NEXT_ADDRESS.getAndIncrement();
        jdbcTemplate.update("DELETE FROM mfa_challenges");
        userRepository.deleteAll();
        companyRepository.deleteAll();
        auditLogs.deleteAll();
        companyRepository.save(Company.builder().id("comp-01").name("MilliKart LLC").status("ACTIVE").build());
        saveUser(ADMIN, "SYSTEM_ADMIN", null);
        saveUser(HEAD, "COMPANY_HEAD", "comp-01");
        saveUser(MANAGER, "COMPANY_MANAGER", "comp-01");
    }

    // Главный путь: первый вход после выкладки подключает фактор, сессия — только после первого верного кода.
    @Test
    void theFirstSignIn_setsTheSecondFactorUp_andOnlyAnAcceptedCodeOpensTheSession() throws Exception {
        JsonNode step = body(login(MANAGER, PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired", is(true)))
                .andExpect(jsonPath("$.mfaSetupRequired", is(true)))
                .andExpect(jsonPath("$.token", nullValue()))
                .andExpect(jsonPath("$.refreshToken", nullValue())));
        String mfaToken = step.get("mfaToken").asText();

        verify(mfaToken, "123456").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Set up the second factor first")));

        JsonNode setup = body(setup(mfaToken).andExpect(status().isOk()));
        String secret = setup.get("secret").asText();
        assertEquals(32, secret.length(), "160 bits in base32");
        assertTrue(setup.get("otpauthUri").asText().startsWith("otpauth://totp/MilliKart%20Merchant%20Portal:"));
        assertTrue(setup.get("otpauthUri").asText().contains("secret=" + secret));
        assertNull(user(MANAGER).getMfaEnabledAt(), "a secret is not set up until its first code is accepted");
        assertFalse(user(MANAGER).getMfaSecret().contains(secret), "the secret is stored encrypted");

        verify(mfaToken, codeOf(secret)).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.mfaRequired", is(false)));

        assertNotNull(user(MANAGER).getMfaEnabledAt());
        assertEquals(1, records("USER", "MFA_ENROLL").size());
        assertTrue(records("AUTH", "LOGIN").stream()
                .anyMatch(r -> r.getOutcome() == AuditOutcome.SUCCESS && r.getDetails().contains("second factor verified")));

        // Шаг входа одноразовый, а подключённый фактор заново не выдаётся — знающий пароль не подменит телефон.
        verify(mfaToken, codeOf(secret)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is(INVALID_STEP)));
        String next = body(login(MANAGER, PASSWORD).andExpect(jsonPath("$.mfaSetupRequired", is(false))))
                .get("mfaToken").asText();
        setup(next).andExpect(status().isConflict());
    }

    // PCI DSS 8.5.1: подсмотренный код второй раз не входит, даже в своём же шаге времени.
    @Test
    void anAcceptedCode_isNeverAcceptedAgain() throws Exception {
        String secret = enroll(MANAGER).secret();
        long enrolledAt = user(MANAGER).getMfaLastStep();

        String mfaToken = body(login(MANAGER, PASSWORD)).get("mfaToken").asText();
        verify(mfaToken, codeAt(secret, enrolledAt)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid code")));
        verify(mfaToken, codeAt(secret, enrolledAt + 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
        assertEquals(enrolledAt + 1, user(MANAGER).getMfaLastStep());
    }

    // Неверный код — неудача входа: шесть подряд запирают учётку, как пароль. Верный пароль между ними счётчик не
    // обнуляет — иначе пароль, введённый каждые пять кодов, давал бы перебирать коды без конца.
    @Test
    void wrongCodes_lockTheAccount_andACorrectPasswordBetweenThemDoesNotResetTheCount() throws Exception {
        String secret = enroll(MANAGER).secret();

        String first = body(login(MANAGER, PASSWORD)).get("mfaToken").asText();
        for (int i = 0; i < 3; i++) {
            verify(first, wrongCodeFor(secret)).andExpect(status().isBadRequest());
        }
        String second = body(login(MANAGER, PASSWORD).andExpect(status().isOk())).get("mfaToken").asText();
        assertEquals(3, user(MANAGER).getFailedLoginAttempts(), "a correct password must not clear wrong codes");
        for (int i = 0; i < 3; i++) {
            verify(second, wrongCodeFor(secret)).andExpect(status().isBadRequest());
        }

        assertNotNull(user(MANAGER).getLockoutUntil(), "six failures lock the account");
        verify(second, codeOf(secret)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Account is locked")));
        login(MANAGER, PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Account is locked")));
        assertEquals(1, records("AUTH", "LOCKOUT").size());
        assertEquals(6, records("AUTH", "LOGIN").stream()
                .filter(r -> "Login refused: wrong second-factor code".equals(r.getDetails())).count());
    }

    // Принятый код снимает накопленные неудачи — как верный пароль без второго фактора.
    @Test
    void anAcceptedCode_clearsTheFailures() throws Exception {
        String secret = enroll(MANAGER).secret();
        String mfaToken = body(login(MANAGER, PASSWORD)).get("mfaToken").asText();
        verify(mfaToken, wrongCodeFor(secret)).andExpect(status().isBadRequest());
        assertEquals(1, user(MANAGER).getFailedLoginAttempts());

        verify(mfaToken, nextCodeOf(secret)).andExpect(status().isOk());
        assertEquals(0, user(MANAGER).getFailedLoginAttempts());
    }

    @Test
    void anUnknownOrExpiredStep_isRefused() throws Exception {
        verify("no-such-step", "123456").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is(INVALID_STEP)));
        setup("no-such-step").andExpect(status().isUnauthorized());

        String mfaToken = body(login(MANAGER, PASSWORD)).get("mfaToken").asText();
        jdbcTemplate.update("UPDATE mfa_challenges SET expires_at = ?", Timestamp.from(Instant.now().minusSeconds(1)));
        setup(mfaToken).andExpect(status().isUnauthorized());
        verify(mfaToken, "123456").andExpect(status().isUnauthorized());
    }

    // Обязательная смена пароля (Р-100) ведёт не к сессии, а к тому же шагу второго фактора.
    @Test
    void anIssuedPassword_isChangedFirst_thenTheSecondFactorIsAsked() throws Exception {
        User manager = user(MANAGER);
        manager.setPasswordChangeRequired(true);
        userRepository.save(manager);

        login(MANAGER, PASSWORD).andExpect(jsonPath("$.passwordChangeRequired", is(true)))
                .andExpect(jsonPath("$.mfaRequired", is(false)));
        String mfaToken = body(mockMvc.perform(fromClient(post("/api/v1/auth/change-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest(MANAGER, PASSWORD, "OwnPassword456!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", nullValue()))
                .andExpect(jsonPath("$.mfaRequired", is(true)))
                .andExpect(jsonPath("$.mfaSetupRequired", is(true))))
                .get("mfaToken").asText();

        String secret = body(setup(mfaToken)).get("secret").asText();
        verify(mfaToken, codeOf(secret)).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }

    // Пароль сбросили, пока шаг ждал кода: этот вход начат старым паролем и сессии не даёт.
    @Test
    void aPasswordResetAfterThePasswordStep_voidsTheStep() throws Exception {
        String secret = enroll(MANAGER).secret();
        String mfaToken = body(login(MANAGER, PASSWORD)).get("mfaToken").asText();

        mockMvc.perform(patch("/api/v1/users/" + user(MANAGER).getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenOf(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest(null, null, "ResetPassword789!", null, null, null, null))))
                .andExpect(status().isOk());

        verify(mfaToken, nextCodeOf(secret)).andExpect(status().isUnauthorized());
    }

    // Потерянный телефон: администратор сбрасывает фактор, сессии гаснут, при входе — подключение заново.
    @Test
    void aReset_endsTheSessions_andTheFactorIsSetUpAgainAtTheNextSignIn() throws Exception {
        String refreshToken = enroll(MANAGER).session().get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/users/" + user(MANAGER).getId() + "/mfa-reset")
                        .header(HttpHeaders.AUTHORIZATION, tokenOf(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaEnabled", is(false)));

        assertNull(user(MANAGER).getMfaSecret());
        mockMvc.perform(fromClient(post("/api/v1/auth/refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
        login(MANAGER, PASSWORD).andExpect(jsonPath("$.mfaSetupRequired", is(true)));
        List<AuditLog> resets = records("USER", "MFA_RESET");
        assertEquals(1, resets.size());
        assertEquals(ADMIN, resets.get(0).getPerformedBy());
    }

    // Сбрасывают по правилам правки пользователя (Р-90), себе — никто: иначе знающий пароль и захвативший
    // сессию снял бы фактор с себя сам.
    @Test
    void aResetFollowsTheUserEditRules_andNobodyResetsTheirOwn() throws Exception {
        UUID adminId = user(ADMIN).getId();
        UUID headId = user(HEAD).getId();
        UUID managerId = user(MANAGER).getId();

        resetBy(ADMIN, adminId).andExpect(status().isForbidden());
        resetBy(HEAD, headId).andExpect(status().isForbidden());
        resetBy(MANAGER, headId).andExpect(status().isForbidden());
        resetBy(HEAD, adminId).andExpect(status().isForbidden());
        resetBy(HEAD, managerId).andExpect(status().isOk());
        resetBy(ADMIN, headId).andExpect(status().isOk());

        assertTrue(records("USER", "MFA_RESET").stream()
                .filter(r -> r.getOutcome() == AuditOutcome.DENIED)
                .anyMatch(r -> r.getDetails().contains("its own second factor")));
    }

    // Вход с подключением фактора до конца: секрет — для следующих кодов, сессия — для её отзыва.
    private Enrolled enroll(String username) throws Exception {
        String mfaToken = body(login(username, PASSWORD)).get("mfaToken").asText();
        String secret = body(setup(mfaToken).andExpect(status().isOk())).get("secret").asText();
        JsonNode session = body(verify(mfaToken, codeOf(secret)).andExpect(status().isOk()));
        return new Enrolled(secret, session);
    }

    private void saveUser(String username, String role, String companyId) {
        userRepository.save(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName(username)
                .role(role)
                .companyId(companyId)
                .status("ACTIVE")
                .build());
    }

    private User user(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    // Пары токенов через вход здесь не получить без кода — токен выписывается напрямую, как в тестах других модулей.
    private String tokenOf(String username) {
        User user = user(username);
        return "Bearer " + jwtProvider.generateToken(user.getId().toString(), username, user.getRole(), user.getCompanyId());
    }

    private ResultActions resetBy(String actor, UUID target) throws Exception {
        return mockMvc.perform(post("/api/v1/users/" + target + "/mfa-reset")
                .header(HttpHeaders.AUTHORIZATION, tokenOf(actor)));
    }

    private List<AuditLog> records(String entityType, String action) {
        return auditLogs.findAll().stream()
                .filter(r -> entityType.equals(r.getEntityType()) && action.equals(r.getAction()))
                .toList();
    }

    private static String codeOf(String secret) {
        return codeAt(secret, Totp.stepAt(Instant.now()));
    }

    // Код следующего шага: принимается (окно ±1), даже если текущий только что израсходован.
    private static String nextCodeOf(String secret) {
        return codeAt(secret, Totp.stepAt(Instant.now()) + 1);
    }

    private static String codeAt(String secret, long step) {
        return Totp.code(Totp.fromBase32(secret), step);
    }

    // Не совпадает ни с одним кодом окна сервера — и при переходе на следующий шаг посреди теста.
    private static String wrongCodeFor(String secret) {
        long now = Totp.stepAt(Instant.now());
        Set<String> window = Set.of(codeAt(secret, now - 1), codeAt(secret, now), codeAt(secret, now + 1),
                codeAt(secret, now + 2));
        for (int candidate = 0; ; candidate++) {
            String code = String.format("%06d", candidate);
            if (!window.contains(code)) {
                return code;
            }
        }
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(fromClient(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))));
    }

    private ResultActions setup(String mfaToken) throws Exception {
        return mockMvc.perform(fromClient(post("/api/v1/auth/mfa/setup"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new MfaSetupRequest(mfaToken))));
    }

    private ResultActions verify(String mfaToken, String code) throws Exception {
        return mockMvc.perform(fromClient(post("/api/v1/auth/mfa/verify"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new MfaVerifyRequest(mfaToken, code))));
    }

    private MockHttpServletRequestBuilder fromClient(MockHttpServletRequestBuilder request) {
        return request.with(servletRequest -> {
            servletRequest.setRemoteAddr(clientAddress);
            return servletRequest;
        });
    }

    private JsonNode body(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
