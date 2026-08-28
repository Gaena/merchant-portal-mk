package az.millikart.auth;

import az.millikart.auth.domain.Company;
import az.millikart.auth.domain.User;
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

    // Миграция больше не заводит админа (P0-6), поэтому фикстура создаёт своего. Пароль отвечает
    // той же политике, которую API требует от любого аккаунта.
    private static final String ADMIN_PASSWORD = "AdminPassword123!";

    // Единственный ответ на любой отказ во входе — см. AuthService.INVALID_CREDENTIALS.
    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    // auth.login.rate-limit.max-failures в тестовом профиле, как в production.
    private static final int MAX_FAILURES_PER_ADDRESS = 10;

    private String adminToken;

    @BeforeEach
    public void setup() throws Exception {
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
        );

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

        LoginRequest headLogin = new LoginRequest("head@comp01.com", "HeadPassword123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(headLogin)))
                .andExpect(status().isOk())
                .andReturn();

        String headToken = "Bearer " + objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();

        CreateUserRequest createEmp = new CreateUserRequest(
                "emp@comp01.com",
                "EmployeePass123!",
                "Employee User",
                "COMPANY_EMPLOYEE",
                "comp-01"
        );

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
        );

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

        UpdateUserRequest updateRequest = new UpdateUserRequest("Updated Name", null, "NewSecurePass123!", "ACTIVE");
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

    // Без сброса один общий офисный адрес исчерпает попытки за утро обычных опечаток. Для отказов
    // взяты несуществующие логины, чтобы собственная блокировка аккаунта после шести отказов
    // не мешала измерению.
    @Test
    @DisplayName("20. a successful login gives the address its full allowance back")
    public void successfulLogin_resetsTheAddressCounter() throws Exception {
        String clientIp = "198.51.100.20";
        for (int i = 0; i < MAX_FAILURES_PER_ADDRESS - 1; i++) {
            login("nobody" + i + "@millikart.az", "WrongPass123!", clientIp).andExpect(status().isBadRequest());
        }

        login("admin@millikart.az", ADMIN_PASSWORD, clientIp).andExpect(status().isOk());

        // Без сброса вторая неудача этой партии была бы уже 429.
        for (int i = 0; i < MAX_FAILURES_PER_ADDRESS - 1; i++) {
            login("nobody" + i + "@millikart.az", "WrongPass123!", clientIp).andExpect(status().isBadRequest());
        }
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
