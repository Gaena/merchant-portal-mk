package az.millikart.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

// Что фильтр обещает логам. Истёкший токен — штатный поток раз в 15 минут у каждого вошедшего, и он не
// пишется ни ERROR, ни WARN: раньше каждый давал ERROR со стектрейсом. Поддельный — WARN без стектрейса.
// Логин вошедшего стоит в MDC ровно на время запроса: потоки контейнера переиспользуются.
class JwtAuthFilterTest {

    private static final String SECRET = "test-only-jwt-secret-not-used-anywhere-else-0123456789";
    private static final String OTHER_SECRET = "another-test-only-jwt-secret-0123456789-abcdef";
    private static final String SECURE_PATH = "/api/v1/payment-links";

    private final JwtProvider provider = new JwtProvider(SECRET, 60_000);
    private Logger filterLogger;
    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void captureLogs() {
        filterLogger = (Logger) LoggerFactory.getLogger(JwtAuthFilter.class);
        events = new ListAppender<>();
        events.start();
        filterLogger.addAppender(events);
    }

    @AfterEach
    void releaseLogs() {
        filterLogger.detachAppender(events);
    }

    @Test
    void anExpiredToken_isRefusedWithoutAnErrorOrAWarning() throws Exception {
        String expired = new JwtProvider(SECRET, -60_000).generateToken("1", "head@comp1.com", "COMPANY_HEAD", "comp-01");

        MockHttpServletResponse response = run(expired, (request, ignored) -> {
            throw new AssertionError("an expired token must not reach the controller");
        });

        assertEquals(401, response.getStatus());
        assertTrue(atLeast(Level.WARN).isEmpty(), atLeast(Level.WARN).toString());
    }

    @Test
    void aForgedToken_isOneWarningWithoutAStackTrace() throws Exception {
        String forged = new JwtProvider(OTHER_SECRET, 60_000).generateToken("1", "head@comp1.com", "SYSTEM_ADMIN", null);

        MockHttpServletResponse response = run(forged, (request, ignored) -> {
            throw new AssertionError("a forged token must not reach the controller");
        });

        assertEquals(401, response.getStatus());
        List<ILoggingEvent> warnings = atLeast(Level.WARN);
        assertEquals(1, warnings.size(), warnings.toString());
        assertEquals(Level.WARN, warnings.getFirst().getLevel());
        assertNull(warnings.getFirst().getThrowableProxy(), "no stack trace for a refused token");
    }

    // JWT-DEFAULTS: подписанный токен без роли получал COMPANY_EMPLOYEE, без логина — имя system, которым в
    // журнале подписаны автоматические действия. Против fail-closed (AGENTS §6): теперь такой токен — 401.
    @Test
    void aSignedTokenWithoutRoleOrSubject_isRefused() throws Exception {
        for (String token : new String[] {
                provider.generateToken("1", "head@comp1.com", null, "comp-01"),
                provider.generateToken("1", "head@comp1.com", "", "comp-01"),
                provider.generateToken("1", null, "COMPANY_HEAD", "comp-01")}) {
            MockHttpServletResponse response = run(token, (request, ignored) -> {
                throw new AssertionError("a token without role or subject must not reach the controller");
            });

            assertEquals(401, response.getStatus());
        }
    }

    @Test
    void theSignedInLogin_isInTheMdcForTheRequestOnly() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        MockHttpServletResponse response = run(provider.generateToken("1", "head@comp1.com", "COMPANY_HEAD", "comp-01"),
                (request, ignored) -> seen.set(MDC.get(JwtAuthFilter.MDC_USER_KEY)));

        assertEquals(200, response.getStatus());
        assertEquals("head@comp1.com", seen.get());
        assertNull(MDC.get(JwtAuthFilter.MDC_USER_KEY), "the login must not outlive the request on this thread");
    }

    // Р-132: право на DMS-ссылки даёт только claim dmsLinks со значением true. Токен без claim (выданный до Р-132)
    // и строка "true" права не дают: умолчание открыло бы DMS тому, кому его запретили.
    @Test
    void theDmsLinksClaim_grantsTheRightOnlyWhenItIsTrue() throws Exception {
        assertTrue(principalOf(provider.generateToken("1", "head@comp1.com", "COMPANY_HEAD", "comp-01", true))
                .isDmsLinksAllowed());
        assertFalse(principalOf(provider.generateToken("1", "head@comp1.com", "COMPANY_HEAD", "comp-01", false))
                .isDmsLinksAllowed());
        assertFalse(principalOf(provider.generateToken("1", "head@comp1.com", "COMPANY_HEAD", "comp-01"))
                .isDmsLinksAllowed());

        String withoutTheClaim = Jwts.builder()
                .setClaims(Map.of("userId", "1", "role", "COMPANY_HEAD"))
                .setSubject("head@comp1.com")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
        assertFalse(principalOf(withoutTheClaim).isDmsLinksAllowed());
        String asText = Jwts.builder()
                .setClaims(Map.of("userId", "1", "role", "COMPANY_HEAD", JwtProvider.DMS_LINKS_CLAIM, "true"))
                .setSubject("head@comp1.com")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
        assertFalse(principalOf(asText).isDmsLinksAllowed());
    }

    private UserPrincipal principalOf(String token) throws Exception {
        AtomicReference<UserPrincipal> seen = new AtomicReference<>();
        MockHttpServletResponse response = run(token, (request, ignored) ->
                seen.set((UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal()));
        assertEquals(200, response.getStatus());
        return seen.get();
    }

    // Статический токен даёт SYSTEM_ADMIN без пароля. Включённый флаг без значения — отказ старта:
    // иначе пустой Bearer или любая строка совпали бы с пустым токеном.
    @Test
    void staticTokenEnabledWithoutAValue_refusesToStart() {
        for (String empty : new String[] {"", "   ", null}) {
            assertThrows(IllegalStateException.class, () -> filter(empty, true));
        }
    }

    // Выключенный флаг — значение токена ничего не открывает, даже если оно задано: это просто кривой JWT.
    @Test
    void staticToken_whenDisabled_isRefusedLikeAnyInvalidToken() throws Exception {
        MockHttpServletResponse response = run(filter("static-integration-token", false), "static-integration-token",
                (request, ignored) -> {
                    throw new AssertionError("a disabled static token must not reach the controller");
                });

        assertEquals(401, response.getStatus());
    }

    // Включённый флаг: верный токен — администратор без компании, неверный — 401. Ловит сравнение,
    // пропускающее префикс или пустую строку, и компанию, подставленную статическому входу.
    @Test
    void staticToken_whenEnabled_signsInAnAdminWithoutACompany_andOnlyForTheExactValue() throws Exception {
        JwtAuthFilter filter = filter("static-integration-token", true);
        AtomicReference<UserPrincipal> seen = new AtomicReference<>();

        MockHttpServletResponse accepted = run(filter, "static-integration-token", (request, ignored) ->
                seen.set((UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal()));
        assertEquals(200, accepted.getStatus());
        assertEquals(Role.SYSTEM_ADMIN, UserPrincipal.getRole(seen.get()));
        assertNull(UserPrincipal.getCompanyId(seen.get()));

        for (String wrong : new String[] {"static-integration", "static-integration-token-x", "STATIC-INTEGRATION-TOKEN"}) {
            MockHttpServletResponse refused = run(filter, wrong, (request, ignored) -> {
                throw new AssertionError("a wrong static token must not reach the controller: " + wrong);
            });
            assertEquals(401, refused.getStatus(), wrong);
        }
    }

    private MockHttpServletResponse run(String token, FilterChain chain) throws Exception {
        return run(filter("", false), token, chain);
    }

    private JwtAuthFilter filter(String staticToken, boolean staticTokenEnabled) {
        return new JwtAuthFilter(provider, new SecurityErrorResponder(new ObjectMapper().findAndRegisterModules()),
                staticToken, staticTokenEnabled, false);
    }

    private MockHttpServletResponse run(JwtAuthFilter filter, String token, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", SECURE_PATH);
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private List<ILoggingEvent> atLeast(Level level) {
        return events.list.stream().filter(event -> event.getLevel().isGreaterOrEqual(level)).toList();
    }
}
