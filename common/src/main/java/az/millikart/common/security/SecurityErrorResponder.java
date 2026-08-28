package az.millikart.common.security;

import az.millikart.common.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

// Пишет отказы, случившиеся до контроллера. Их два, и клиент не должен их различать: JwtAuthFilter
// отвергает запрос с негодным токеном, Spring Security — запрос к пути, требующему аутентификации;
// без этого второй вернул бы HTML-страницу контейнера или пустое тело. ObjectMapper берётся
// приложения намеренно: иначе timestamp отрендерится сырым epoch, а не как в остальных ответах.
@Component
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityErrorResponder.class);

    // Намеренно тот же текст, что JwtAuthFilter шлёт при отсутствии заголовка: какой из двух слоёв
    // отказал — наша деталь реализации, а не сведения для вызывающего.
    static final String MISSING_CREDENTIALS_MESSAGE = "Missing or invalid Authorization header";

    private final ObjectMapper objectMapper;

    public SecurityErrorResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("Unauthenticated request rejected: {} {}", request.getMethod(), request.getRequestURI());
        writeUnauthorized(request, response, MISSING_CREDENTIALS_MESSAGE);
    }

    // Аутентифицирован, но не допущен: 403, а не 401 — вызывающий уже доказал, кто он. 401
    // отправил бы легитимного пользователя логиниться заново там, где повторный вход ничего не
    // исправит. 403 как «доступ запрещён» читается по всему проекту (см. InvalidStateException).
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("Access denied: {} {}", request.getMethod(), request.getRequestURI());
        write(request, response, HttpStatus.FORBIDDEN, "Access denied");
    }

    // Вызывается из JwtAuthFilter, чтобы его 401 имел ровно ту же форму тела, что и наши.
    public void writeUnauthorized(HttpServletRequest request,
                                  HttpServletResponse response,
                                  String message) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, message);
    }

    private void write(HttpServletRequest request,
                       HttpServletResponse response,
                       HttpStatus status,
                       String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        )));
    }
}
