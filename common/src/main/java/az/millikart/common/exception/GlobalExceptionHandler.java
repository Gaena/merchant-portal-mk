package az.millikart.common.exception;

import az.millikart.common.dto.ErrorResponse;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Маркер мониторинга: движение денег с неизвестным исходом, человек сверяет его с эквайером.
    private static final String PAYMENT_OUTCOME_UNKNOWN_MARKER = "PAYMENT_OUTCOME_UNKNOWN";

    private static final Pattern SIMPLE_FIELD_NAME = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Pattern SIMPLE_CONSTRAINT_NAME = Pattern.compile("[A-Za-z0-9_.]{1,128}");
    private static final Pattern SQL_STATE = Pattern.compile("[0-9A-Z]{5}");

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // 502, а не 400: 400 сказал бы мерчанту, что операцию безопасно повторить, а у эквайера она могла
    // пройти. Следующий шаг — проверить статус, а не повторять.
    @ExceptionHandler(PaymentOutcomeUnknownException.class)
    public ResponseEntity<ErrorResponse> handlePaymentOutcomeUnknown(PaymentOutcomeUnknownException ex,
                                                                     HttpServletRequest request) {
        log.error("{}: {} {} left an acquirer operation unconfirmed — reconcile before retrying. Cause: {}",
                PAYMENT_OUTCOME_UNKNOWN_MARKER, request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.BAD_GATEWAY,
                "No confirmation received from the acquirer. Check the transaction status before retrying.",
                request);
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(InvalidStateException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // Тело 429 без счётчиков и порогов: иначе вызывающий держится прямо под лимитом. Retry-After не
    // меньше 1: 0 читается как «повторяй сейчас».
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest request) {
        long retryAfterSeconds = Math.max(1, ex.getRetryAfter().toSeconds());
        log.warn("Refused {} {} as too frequent; Retry-After: {}s", request.getMethod(), request.getRequestURI(), retryAfterSeconds);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(body(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "The resource was updated concurrently, please retry", request);
    }

    // Блокировка строки занята другим запросом (NOWAIT в pbl): до эквайера этот запрос не дошёл,
    // повтор безопасен — поэтому 409, а не 500.
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handlePessimisticLock(PessimisticLockingFailureException ex, HttpServletRequest request) {
        log.info("Refused {} {}: the row is locked by a concurrent request", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.CONFLICT, "The resource is being changed by another request, please retry", request);
    }

    // Ограничение базы — ошибка клиента, а не сбой: гонка «проверил — вставил» или значение, которое не
    // остановила проверка DTO (DB-CONSTRAINT-500). Класс 22 (длина, формат) — 400, остальное — 409. Текст
    // драйвера цитирует значения: ни в ответ, ни в лог, только SQLState и имя ограничения.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String sqlState = sqlStateOf(ex);
        log.warn("Refused {} {}: the database rejected the data (SQLState {}, constraint {})",
                request.getMethod(), request.getRequestURI(), sqlState, constraintNameOf(ex));
        if (sqlState.startsWith("22")) {
            return build(HttpStatus.BAD_REQUEST, "A field value is too long or has an invalid format", request);
        }
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data", request);
    }

    private static String sqlStateOf(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return SQL_STATE.matcher(sql.getSQLState()).matches() ? sql.getSQLState() : "?";
            }
        }
        return "?";
    }

    // Имя ограничения Hibernate у H2 вырезает из текста драйвера, и в нём бывают значения: в лог — только простое.
    private static String constraintNameOf(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                String name = violation.getConstraintName();
                return SIMPLE_CONSTRAINT_NAME.matcher(name).matches() ? name : "?";
            }
        }
        return "-";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(GlobalExceptionHandler::formatFieldError)
                .orElseGet(() -> ex.getBindingResult().getAllErrors().stream()
                        .findFirst()
                        .map(error -> error.getDefaultMessage())
                        .orElse("Validation failed"));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    // Нет обязательного параметра или он не того типа — ошибка клиента, 400 (Р-103). Имя параметра —
    // в ответ, присланное значение не отражаем.
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            org.springframework.web.bind.MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.debug("Missing parameter {} in {} {}", ex.getParameterName(), request.getMethod(), request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "Required parameter '" + ex.getParameterName() + "' is missing", request);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.debug("Parameter {} of the wrong type in {} {}", ex.getName(), request.getMethod(), request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "Parameter '" + ex.getName() + "' has an invalid value", request);
    }

    // Сообщение Jackson цитирует кусок тела: пароль без кавычек ушёл бы в лог (P0-9). В лог — только вид
    // ошибки, место и имя поля.
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.warn("Unreadable request body in {} {}: {}", request.getMethod(), request.getRequestURI(), whereUnreadable(ex));
        return build(HttpStatus.BAD_REQUEST, "Invalid request payload format or parameter value", request);
    }

    private static String whereUnreadable(Exception ex) {
        if (!(ex.getCause() instanceof JsonProcessingException json)) {
            return ex.getCause() == null ? "no readable body" : ex.getCause().getClass().getSimpleName();
        }
        StringBuilder where = new StringBuilder(json.getClass().getSimpleName());
        JsonLocation location = json.getLocation();
        if (location != null && location.getLineNr() > 0) {
            where.append(" at line ").append(location.getLineNr()).append(", column ").append(location.getColumnNr());
        }
        if (json instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            where.append(", field ").append(mapping.getPath().stream()
                    .map(GlobalExceptionHandler::fieldName)
                    .collect(Collectors.joining(".")));
        }
        return where.toString();
    }

    // Ключ у Map-поля — ввод клиента: в лог только простое имя.
    private static String fieldName(JsonMappingException.Reference reference) {
        String name = reference.getFieldName();
        if (name == null) {
            return "[" + reference.getIndex() + "]";
        }
        return SIMPLE_FIELD_NAME.matcher(name).matches() ? name : "?";
    }

    // Путь без обработчика — 404, а не 500 с ERROR за опечатку в URL. Сюда же попадают actuator и
    // выключенный springdoc на рабочем порту (P1-1).
    @ExceptionHandler({
            org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNoHandler(Exception ex, HttpServletRequest request) {
        log.debug("No handler for {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "Endpoint not found", request);
    }

    // Верный путь, неверный метод — 405, а не 500 со стектрейсом. Allow на 405 требует RFC 9110.
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        Set<HttpMethod> allowed = ex.getSupportedHttpMethods();
        log.debug("Method {} not supported for {}; allowed: {}", request.getMethod(), request.getRequestURI(), allowed);

        String message = allowed == null || allowed.isEmpty()
                ? "Method " + request.getMethod() + " is not supported for this endpoint"
                : "Method " + request.getMethod() + " is not supported for this endpoint; use "
                        + allowed.stream().map(HttpMethod::name).sorted().collect(Collectors.joining(", "));

        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (allowed != null && !allowed.isEmpty()) {
            response.allow(allowed.toArray(new HttpMethod[0]));
        }
        return response.body(body(HttpStatus.METHOD_NOT_ALLOWED, message, request));
    }

    // Тело, которое эндпоинт не читает (form-post, нет Content-Type), — ошибка клиента: 415, а не 500.
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            org.springframework.web.HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.debug("Unsupported Content-Type {} for {} {}", ex.getContentType(), request.getMethod(), request.getRequestURI());
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type " + (ex.getContentType() != null ? ex.getContentType() : "(none)")
                        + " is not supported by this endpoint; send application/json", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
    }

    private static String formatFieldError(FieldError error) {
        if (error.getDefaultMessage() != null && !error.getDefaultMessage().isBlank()) {
            return error.getDefaultMessage();
        }
        return "Field '" + error.getField() + "' is invalid";
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(body(status, message, request));
    }

    // Единая форма любого ответа об ошибке.
    private ErrorResponse body(HttpStatus status, String message, HttpServletRequest request) {
        return new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
    }
}
