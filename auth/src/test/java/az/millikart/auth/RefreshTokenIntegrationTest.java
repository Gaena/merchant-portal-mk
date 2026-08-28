package az.millikart.auth;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.auth.domain.Company;
import az.millikart.auth.domain.RefreshToken;
import az.millikart.auth.domain.User;
import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.RefreshTokenRepository;
import az.millikart.auth.repository.UserRepository;
import az.millikart.auth.service.RefreshTokenService;
import az.millikart.common.security.JwtProvider;
import az.millikart.common.security.PublicEndpoints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// P1-12: refresh-токены с ротацией, logout, отзыв при блокировке и удалении. Тестовый профиль
// даёт access-токену 1 час вместо продовых 24 — именно это позволяет login_returnsRefreshToken
// поймать зашитый expiresIn = 86400. TTL refresh и окно грации продовые (30 дней / 10 секунд);
// единственный сценарий с другой грацией живёт в WithZeroGrace.
@SpringBootTest
@AutoConfigureMockMvc
public class RefreshTokenIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@millikart.az";
    private static final String ADMIN_PASSWORD = "AdminPassword123!";
    private static final String HEAD_EMAIL = "head@comp01.com";
    private static final String HEAD_PASSWORD = "HeadPassword123!";

    // Тестовый профиль: pbl.security.jwt.expiration-ms = 3600000.
    private static final long TEST_ACCESS_TTL_SECONDS = 3600;
    // auth.refresh.ttl = P30D в тестовом профиле, как в production.
    private static final long REFRESH_TTL_SECONDS = Duration.ofDays(30).toSeconds();

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
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID headId;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        companyRepository.save(Company.builder().id("comp-01").name("MilliKart LLC").status("ACTIVE").build());

        userRepository.save(User.builder()
                .username(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .fullName("System Admin")
                .role("SYSTEM_ADMIN")
                .status("ACTIVE")
                .build());
        headId = userRepository.save(User.builder()
                .username(HEAD_EMAIL)
                .passwordHash(passwordEncoder.encode(HEAD_PASSWORD))
                .fullName("Company Head")
                .role("COMPANY_HEAD")
                .companyId("comp-01")
                .status("ACTIVE")
                .build()).getId();
    }

    // 1. login

    @Test
    @DisplayName("1. login returns a refresh token, and expiresIn is the configured lifetime, not 86400")
    void login_returnsRefreshToken() throws Exception {
        JsonNode body = login(HEAD_EMAIL, HEAD_PASSWORD);

        assertNotNull(body.get("token").asText());
        assertEquals("COMPANY_HEAD", body.get("role").asText());
        // Тестовый профиль: 1 час. Старый код возвращал литерал 86400 независимо от конфигурации.
        assertEquals(TEST_ACCESS_TTL_SECONDS, body.get("expiresIn").asLong(),
                "expiresIn must come from pbl.security.jwt.expiration-ms");
        assertEquals(REFRESH_TTL_SECONDS, body.get("refreshExpiresIn").asLong(),
                "refreshExpiresIn must come from auth.refresh.ttl");

        String refreshToken = body.get("refreshToken").asText();
        assertNotNull(refreshToken);
        assertTrue(refreshToken.length() >= 43, "32 random bytes in base64url are 43 characters: " + refreshToken);

        List<RefreshToken> stored = refreshTokenRepository.findAllByUserId(headId);
        assertEquals(1, stored.size());
        assertNull(stored.get(0).getRotatedAt());
        assertNull(stored.get(0).getRevokedAt());
    }

    @Test
    @DisplayName("the database holds the SHA-256 of the refresh token, never the token itself")
    void refreshToken_isStoredOnlyAsSha256Hash() throws Exception {
        String refreshToken = login(HEAD_EMAIL, HEAD_PASSWORD).get("refreshToken").asText();

        List<RefreshToken> all = refreshTokenRepository.findAll();
        assertEquals(1, all.size());
        assertEquals(sha256Hex(refreshToken), all.get(0).getTokenHash());
        assertNotEquals(refreshToken, all.get(0).getTokenHash());
        assertEquals(64, all.get(0).getTokenHash().length());

        // На всякий случай: ни одна колонка строки не содержит сырого токена.
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM refresh_tokens");
        assertEquals(1, rows.size());
        for (Object value : rows.get(0).values()) {
            assertNotEquals(refreshToken, String.valueOf(value), "raw refresh token found in refresh_tokens");
        }
    }

    // 2. Ротация

    @Test
    @DisplayName("2. refresh with a valid token returns a new access token and a new refresh token")
    void refresh_withValidToken_returnsNewPair() throws Exception {
        JsonNode first = login(HEAD_EMAIL, HEAD_PASSWORD);
        String oldAccess = first.get("token").asText();
        String oldRefresh = first.get("refreshToken").asText();

        // В JWT iat/exp — целые секунды: два токена с одними claims внутри одной секунды выйдут
        // побайтово одинаковыми, поэтому переходим через границу секунды.
        Thread.sleep(1100);

        JsonNode second = refresh(oldRefresh).andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.role", is("COMPANY_HEAD")))
                .andExpect(jsonPath("$.expiresIn", is((int) TEST_ACCESS_TTL_SECONDS)))
                .andExpect(jsonPath("$.refreshExpiresIn", is((int) REFRESH_TTL_SECONDS)))
                .andReturn().getResponse().getContentAsString().transform(this::readTree);

        String newAccess = second.get("token").asText();
        String newRefresh = second.get("refreshToken").asText();
        assertNotEquals(oldAccess, newAccess, "a new access token must be issued");
        assertNotEquals(oldRefresh, newRefresh, "a new refresh token must be issued");
        assertEquals(HEAD_EMAIL, jwtProvider.validateAndGetClaims(newAccess).getSubject(),
                "the new access token must be a valid JWT for the same user");

        // Та же семья: старый на пенсии, новый живой.
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserId(headId);
        assertEquals(2, tokens.size());
        RefreshToken old = tokens.stream().filter(t -> t.getTokenHash().equals(sha256Hex(oldRefresh))).findFirst().orElseThrow();
        RefreshToken fresh = tokens.stream().filter(t -> t.getTokenHash().equals(sha256Hex(newRefresh))).findFirst().orElseThrow();
        assertEquals(old.getFamilyId(), fresh.getFamilyId(), "rotation must stay in the same family");
        assertNotNull(old.getRotatedAt(), "the presented token must be marked rotated");
        assertNull(old.getRevokedAt());
        assertNull(fresh.getRotatedAt());
        assertNull(fresh.getRevokedAt());
    }

    @Test
    @DisplayName("4. a rotated token presented again within the grace window is served, and the family stays alive")
    void refresh_withRotatedTokenWithinGrace_succeeds() throws Exception {
        String original = login(HEAD_EMAIL, HEAD_PASSWORD).get("refreshToken").asText();

        String successor = refresh(original).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::readTree)
                .get("refreshToken").asText();

        // Вторая вкладка, тот же токен, глубоко внутри окна в 10 с: обслужен, а не наказан.
        String secondSuccessor = refresh(original).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::readTree)
                .get("refreshToken").asText();
        assertNotEquals(successor, secondSuccessor);

        // Ничего не отозвано, оба преемника работают.
        assertTrue(refreshTokenRepository.findAllByUserId(headId).stream().noneMatch(RefreshToken::isRevoked),
                "a grace-window replay must not revoke anything");
        refresh(successor).andExpect(status().isOk());
        refresh(secondSuccessor).andExpect(status().isOk());
    }

    // logout

    @Test
    @DisplayName("5. refresh after logout returns 401 — logout revokes the whole family, not one token")
    void refresh_afterLogout_returns401() throws Exception {
        String first = login(HEAD_EMAIL, HEAD_PASSWORD).get("refreshToken").asText();
        String second = refresh(first).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::readTree)
                .get("refreshToken").asText();

        logout(second).andExpect(status().isNoContent());

        refresh(second).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid refresh token")));
        // Предшественник на пенсии внутри окна грации, но семья мертва: тоже 401.
        refresh(first).andExpect(status().isUnauthorized());

        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserId(headId);
        assertEquals(2, tokens.size());
        assertTrue(tokens.stream().allMatch(RefreshToken::isRevoked), "logout must revoke every token of the family");
    }

    @Test
    @DisplayName("logout ends only the session it was given: another login of the same user survives")
    void logout_leavesOtherSessionsOfTheUserAlone() throws Exception {
        String laptop = login(HEAD_EMAIL, HEAD_PASSWORD).get("refreshToken").asText();
        String phone = login(HEAD_EMAIL, HEAD_PASSWORD).get("refreshToken").asText();

        logout(laptop).andExpect(status().isNoContent());

        refresh(laptop).andExpect(status().isUnauthorized());
        refresh(phone).andExpect(status().isOk());
    }

    @Test
    @DisplayName("6. logout with an unknown token returns 204 — the endpoint is not an existence oracle")
    void logout_withUnknownToken_returns204() throws Exception {
        logout("definitely-not-a-token-" + UUID.randomUUID()).andExpect(status().isNoContent());
        logout("").andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());
    }

    // Заблокированный и удалённый пользователь

    @Test
    @DisplayName("7. refresh by a user who is no longer ACTIVE is refused and revokes the family")
    void refresh_forBlockedUser_returns401AndRevokes() throws Exception {
        String refreshToken = login(HEAD_EMAIL, HEAD_PASSWORD).get("refreshToken").asText();

        // Напрямую через репозиторий, минуя UserService: так проверяется проверка внутри самого
        // refresh (шаг 5), а не отзыв при смене статуса — это тест 8.
        User head = userRepository.findById(headId).orElseThrow();
        head.setStatus("BLOCKED");
        userRepository.save(head);
        assertTrue(refreshTokenRepository.findAllByUserId(headId).stream().noneMatch(RefreshToken::isRevoked),
                "precondition: nothing revoked yet, the token is still live in the database");

        refresh(refreshToken).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid refresh token")));

        assertTrue(refreshTokenRepository.findAllByUserId(headId).stream().allMatch(RefreshToken::isRevoked),
                "the family must be revoked, and the revocation must survive the 401");

        // Реактивация пользователя сессию не воскрешает — токен потерян навсегда.
        head.setStatus("ACTIVE");
        userRepository.save(head);
        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("8. blocking a user via the API revokes all their refresh tokens immediately")
    void blockingUser_revokesHisRefreshTokens() throws Exception {
        String adminAccess = login(ADMIN_EMAIL, ADMIN_PASSWORD).get("token").asText();
        String laptop = login(HEAD_EMAIL, HEAD_PASSWORD).get("refreshToken").asText();
        String phone = login(HEAD_EMAIL, HEAD_PASSWORD).get("refreshToken").asText();
        assertEquals(2, refreshTokenRepository.findAllByUserId(headId).size());

        mockMvc.perform(patch("/api/v1/users/" + headId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRequest(null, null, null, "BLOCKED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("BLOCKED")));

        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserId(headId);
        assertEquals(2, tokens.size());
        assertTrue(tokens.stream().allMatch(RefreshToken::isRevoked), "every session of the blocked user must be revoked");
        refresh(laptop).andExpect(status().isUnauthorized());
        refresh(phone).andExpect(status().isUnauthorized());

        // Собственная сессия админа не тронута.
        assertTrue(refreshTokenRepository.findAll().stream()
                .filter(t -> !t.getUserId().equals(headId))
                .noneMatch(RefreshToken::isRevoked));
    }

    @Test
    @DisplayName("deleting a user revokes all their refresh tokens")
    void deletingUser_revokesHisRefreshTokens() throws Exception {
        String adminAccess = login(ADMIN_EMAIL, ADMIN_PASSWORD).get("token").asText();
        String refreshToken = login(HEAD_EMAIL, HEAD_PASSWORD).get("refreshToken").asText();

        mockMvc.perform(delete("/api/v1/users/" + headId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccess))
                .andExpect(status().isNoContent());

        assertTrue(refreshTokenRepository.findAllByUserId(headId).stream().allMatch(RefreshToken::isRevoked));
        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    // Просроченный и неизвестный токен

    @Test
    @DisplayName("9. refresh with an expired token returns 401")
    void refresh_withExpiredToken_returns401() throws Exception {
        String refreshToken = login(HEAD_EMAIL, HEAD_PASSWORD).get("refreshToken").asText();

        // expires_at на сущности updatable = false, поэтому старим SQL-ом — тем же приёмом,
        // что тесты pbl применяют к created_at.
        jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = ? WHERE user_id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), headId);

        refresh(refreshToken).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid refresh token")));
    }

    @Test
    @DisplayName("10. refresh with an unknown token returns 401")
    void refresh_withUnknownToken_returns401() throws Exception {
        refresh("no-such-token-" + UUID.randomUUID()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("Invalid refresh token")))
                .andExpect(jsonPath("$.path", is("/api/v1/auth/refresh")));

        // Отсутствующий токен — это некорректный запрос, а не провал аутентификации.
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // Публичные пути

    @Test
    @DisplayName("11. refresh and logout are reachable without an Authorization header")
    void refreshEndpoints_areReachableWithoutAccessToken() throws Exception {
        assertTrue(PublicEndpoints.isPublic("/api/v1/auth/refresh"));
        assertTrue(PublicEndpoints.isPublic("/api/v1/auth/logout"));

        String refreshToken = login(HEAD_EMAIL, HEAD_PASSWORD).get("refreshToken").asText();

        // Ни в одном из двух вызовов нет заголовка Authorization — и оба проходят, значит ни
        // JwtAuthFilter, ни Spring Security не вмешались: их 401 сказал бы "Missing or invalid
        // Authorization header", а не обслужил бы запрос.
        String next = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::readTree)
                .get("refreshToken").asText();
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", next))))
                .andExpect(status().isNoContent());
    }

    // Очистка

    @Test
    @DisplayName("cleanup deletes expired rows and leaves live ones alone")
    void deleteExpired_removesOnlyExpiredRows() throws Exception {
        String live = login(HEAD_EMAIL, HEAD_PASSWORD).get("refreshToken").asText();
        String stale = login(ADMIN_EMAIL, ADMIN_PASSWORD).get("refreshToken").asText();
        UUID adminId = userRepository.findByUsername(ADMIN_EMAIL).orElseThrow().getId();
        jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = ? WHERE user_id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), adminId);

        int deleted = refreshTokenService.deleteExpired(Instant.now());

        assertEquals(1, deleted);
        assertEquals(1, refreshTokenRepository.count());
        assertEquals(sha256Hex(live), refreshTokenRepository.findAll().get(0).getTokenHash());
        refresh(stale).andExpect(status().isUnauthorized());
        refresh(live).andExpect(status().isOk());
    }

    // Обнаружение переиспользования (грация = 0)

    // Тесту 3 нужна нулевая грация, а это уже другой Spring-контекст. Контекст объявлен здесь,
    // окружающая фикстура (setUp, хелперы, репозитории) переиспользуется как есть: оба контекста
    // делят одну H2, и записи одного видны другому. Через MockMvc ИМЕННО этого контекста должны
    // идти только вызовы refresh — только там действует rotation-grace=PT0S.
    @Nested
    @SpringBootTest(properties = "auth.refresh.rotation-grace=PT0S")
    @AutoConfigureMockMvc
    @ExtendWith(OutputCaptureExtension.class)
    class WithZeroGrace {

        @Autowired
        private MockMvc zeroGraceMvc;

        @Test
        @DisplayName("3. reuse of a rotated token after the grace window revokes the whole family — the new token dies too")
        void refresh_withRotatedTokenAfterGrace_revokesWholeFamily(CapturedOutput output) throws Exception {
            String original = login(HEAD_EMAIL, HEAD_PASSWORD).get("refreshToken").asText();

            String successor = refresh(zeroGraceMvc, original).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString().transform(RefreshTokenIntegrationTest.this::readTree)
                    .get("refreshToken").asText();
            refresh(zeroGraceMvc, successor).andExpect(status().isOk()); // the successor works before the theft

            // Грация нулевая, любое позднейшее использование — "после окна"; пара мс для ясности.
            Thread.sleep(20);

            // Переиспользование отправленного на пенсию токена.
            refresh(zeroGraceMvc, original).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message", is("Invalid refresh token")));

            // Сигнал для мониторинга сработал...
            assertTrue(output.getOut().contains("REFRESH_TOKEN_REUSE"),
                    "reuse must be logged with the REFRESH_TOKEN_REUSE marker");

            // ...вся семья отозвана в базе, и отзыв пережил 401...
            List<RefreshToken> tokens = refreshTokenRepository.findAllByUserId(headId);
            assertEquals(3, tokens.size());
            assertTrue(tokens.stream().allMatch(RefreshToken::isRevoked), "reuse must revoke every token of the family");
            assertEquals(1, tokens.stream().map(RefreshToken::getFamilyId).distinct().count());

            // ...поэтому и токен, оставшийся у законного владельца, перестаёт работать.
            refresh(zeroGraceMvc, successor).andExpect(status().isUnauthorized());
        }
    }

    // Хелперы

    private JsonNode login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::readTree);
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return refresh(mockMvc, refreshToken);
    }

    private ResultActions refresh(MockMvc mvc, String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // Независимый SHA-256, чтобы тест не заимствовал ту реализацию, которую проверяет.
    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
