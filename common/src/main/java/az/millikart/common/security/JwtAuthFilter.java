package az.millikart.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    // Логин вошедшего — в каждой строке лога запроса (logback-spring.xml): сообщения его не повторяют.
    public static final String MDC_USER_KEY = "user";

    private final JwtProvider jwtProvider;
    private final String fallbackApiToken;
    private final boolean fallbackApiTokenEnabled;
    private final SecurityErrorResponder errorResponder;
    private final boolean swaggerEnabled;

    public JwtAuthFilter(JwtProvider jwtProvider,
                          SecurityErrorResponder errorResponder,
                          @Value("${pbl.security.api-token:}") String fallbackApiToken,
                          @Value("${pbl.security.api-token-enabled:false}") boolean fallbackApiTokenEnabled,
                          @Value("${springdoc.api-docs.enabled:false}") boolean swaggerEnabled) {
        // Статический токен даёт SYSTEM_ADMIN без пароля: дефолта нет (известный дефолт — бэкдор), а
        // включённый флаг без значения роняет старт.
        if (fallbackApiTokenEnabled && (fallbackApiToken == null || fallbackApiToken.isBlank())) {
            throw new IllegalStateException(
                    "pbl.security.api-token-enabled is true but pbl.security.api-token is empty. "
                            + "The static fallback token grants role SYSTEM_ADMIN, so it has no default value.\n"
                            + "How to fix: either set PBL_API_TOKEN_ENABLED=false (recommended — JWT is the "
                            + "normal way in), or set PBL_API_TOKEN to a long random value, e.g. "
                            + "export PBL_API_TOKEN=\"$(openssl rand -base64 32)\"");
        }
        this.jwtProvider = jwtProvider;
        this.errorResponder = errorResponder;
        this.fallbackApiToken = fallbackApiToken;
        this.fallbackApiTokenEnabled = fallbackApiTokenEnabled;
        this.swaggerEnabled = swaggerEnabled;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!requiresAuthentication(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or invalid Authorization header in request to secure endpoint: {}", path);
            writeUnauthorized(request, response, "Missing or invalid Authorization header");
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        String username;
        String userId;
        String role;
        String companyId;

        // Сравнение за постоянное время: equals обрывается на первом несовпавшем символе, и токен, дающий
        // SYSTEM_ADMIN, подбирался бы по времени ответа посимвольно (API-TOKEN-COMPARE).
        if (fallbackApiTokenEnabled && fallbackApiToken != null && !fallbackApiToken.isBlank()
                && MessageDigest.isEqual(fallbackApiToken.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8))) {
            username = "admin@millikart.az";
            userId = "00000000-0000-0000-0000-000000000000";
            role = Role.SYSTEM_ADMIN.name();
            companyId = null;
            log.debug("Fallback static token authentication successful for path: {}", path);
        } else {
            try {
                Claims claims = jwtProvider.validateAndGetClaims(token);
                username = claims.getSubject();
                userId = (String) claims.get("userId");
                if (userId == null) {
                    userId = username;
                }
                role = (String) claims.get("role");
                companyId = (String) claims.get("companyId");
            } catch (ExpiredJwtException e) {
                // Штатно раз в 15 минут у каждого вошедшего: фронтенд обновит токен сам.
                log.debug("Rejected an expired token for {}", path);
                writeUnauthorized(request, response, "Invalid or expired JWT token");
                return;
            } catch (JwtException | IllegalArgumentException e) {
                log.warn("Rejected an invalid token for {}: {}", path, e.getClass().getSimpleName());
                writeUnauthorized(request, response, "Invalid or expired JWT token");
                return;
            } catch (Exception e) {
                log.error("Unexpected failure while reading a token for {}", path, e);
                writeUnauthorized(request, response, "Invalid or expired JWT token");
                return;
            }
        }

        if (userId == null || userId.isBlank()) {
            log.warn("Authentication failed: userId/sub not found in token claims for path: {}", path);
            writeUnauthorized(request, response, "Unauthorized: userId not found in token");
            return;
        }
        // Без роли или логина — отказ, а не умолчание (JWT-DEFAULTS): роль COMPANY_EMPLOYEE дала бы права, которых
        // в токене нет, а логин system подписал бы журнал именем автоматических действий. Наши токены несут оба.
        if (role == null || role.isBlank() || username == null || username.isBlank()) {
            log.warn("Rejected a token without the role or subject claim for {}", path);
            writeUnauthorized(request, response, "Invalid or expired JWT token");
            return;
        }

        // Роль — сырой строкой намеренно: разбирает её UserPrincipal, нераспознанная доходит до
        // сервисов как «нет роли».
        String finalRole = role;
        String finalUsername = username;

        UserPrincipal principal = new UserPrincipal(userId, finalUsername, finalRole, companyId);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        request.setAttribute("username", finalUsername);
        request.setAttribute("userId", userId);
        request.setAttribute("userRole", finalRole);
        request.setAttribute("companyId", companyId);

        MDC.put(MDC_USER_KEY, finalUsername);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_USER_KEY);
            SecurityContextHolder.clearContext();
        }
    }

    // Публичные пути — только PublicEndpoints, общий с SecurityConfig (P1-1); springdoc — по тому же
    // флагу, что и там.
    private boolean requiresAuthentication(String path) {
        if (PublicEndpoints.isPublic(path)) {
            return false;
        }
        return !(swaggerEnabled && PublicEndpoints.isSwagger(path));
    }

    private void writeUnauthorized(HttpServletRequest request,
                                   HttpServletResponse response,
                                   String message) throws IOException {
        errorResponder.writeUnauthorized(request, response, message);
    }
}

