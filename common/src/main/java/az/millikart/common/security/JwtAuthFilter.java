package az.millikart.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        // Статический токен аутентифицирует как SYSTEM_ADMIN без пароля, поэтому встроенного
        // значения у него быть не должно: известный дефолт — это бэкдор. Включён без значения —
        // ошибка конфигурации: флаг поднят, проверка мертва. Поэтому падаем на старте.
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

        if (fallbackApiTokenEnabled && fallbackApiToken != null && !fallbackApiToken.isBlank() && fallbackApiToken.equals(token)) {
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
            } catch (Exception e) {
                log.error("Failed to parse and validate JWT token for path {}", path, e);
                writeUnauthorized(request, response, "Invalid or expired JWT token");
                return;
            }
        }

        if (userId == null || userId.isBlank()) {
            log.warn("Authentication failed: userId/sub not found in token claims for path: {}", path);
            writeUnauthorized(request, response, "Unauthorized: userId not found in token");
            return;
        }

        // Claim остаётся здесь сырой строкой намеренно: разбирает её UserPrincipal, а
        // нераспознанное значение должно дойти до сервисов как «нет роли», а не быть отвергнуто.
        String finalRole = role != null ? role : Role.COMPANY_EMPLOYEE.name();
        String finalUsername = username != null ? username : "system";

        UserPrincipal principal = new UserPrincipal(userId, finalUsername, finalRole, companyId);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        request.setAttribute("username", finalUsername);
        request.setAttribute("userId", userId);
        request.setAttribute("userRole", finalRole);
        request.setAttribute("companyId", companyId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // Публичных путей здесь нет — они в PublicEndpoints, общем с SecurityConfig. Прежняя версия
    // решала по префиксу и пропускала всё, что вне /api/v1/, — так и остались открыты actuator и
    // swagger. Springdoc — единственный условный случай: его пути существуют лишь при
    // springdoc.api-docs.enabled, и SecurityConfig разрешает их по тому же флагу.
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

