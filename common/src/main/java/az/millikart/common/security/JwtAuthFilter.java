package az.millikart.common.security;

import az.millikart.common.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtProvider jwtProvider,
                          @Value("${pbl.security.api-token:pbl-secret-token}") String fallbackApiToken,
                          @Value("${pbl.security.api-token-enabled:false}") boolean fallbackApiTokenEnabled) {
        this.jwtProvider = jwtProvider;
        this.fallbackApiToken = fallbackApiToken;
        this.fallbackApiTokenEnabled = fallbackApiTokenEnabled;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
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
            role = "SYSTEM_ADMIN";
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

        String finalRole = role != null ? role : "COMPANY_EMPLOYEE";
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

    private boolean requiresAuthentication(String path) {
        if (path == null || !path.startsWith("/api/v1/")) {
            return false;
        }
        // Public Auth endpoints
        if (path.startsWith("/api/v1/auth/")) {
            return false;
        }
        // Public PBL endpoints
        if (path.startsWith("/api/v1/payment-links/") && path.endsWith("/open")) {
            return false;
        }
        if (path.startsWith("/api/v1/payment-links/redirect")) {
            return false;
        }
        if (path.startsWith("/api/v1/transactions/") && path.endsWith("/status")) {
            return false;
        }
        return true;
    }

    private void writeUnauthorized(HttpServletRequest request,
                                   HttpServletResponse response,
                                   String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String body = objectMapper.writeValueAsString(new ErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                message,
                request.getRequestURI()
        ));
        response.getWriter().write(body);
    }
}

