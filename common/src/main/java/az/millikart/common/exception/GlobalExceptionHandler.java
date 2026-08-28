package az.millikart.common.exception;

import az.millikart.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
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

    // Маркер для мониторинга: каждое появление — движение денег с неизвестным исходом, и каждое
    // требует, чтобы человек сверил его с эквайером.
    private static final String PAYMENT_OUTCOME_UNKNOWN_MARKER = "PAYMENT_OUTCOME_UNKNOWN";

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

    // 502, а не 400: 400 сказал бы мерчанту, что операция отклонена и её безопасно повторить, — а у
    // эквайера она могла уже пройти. Единственный верный следующий шаг — проверить транзакцию, а не
    // выстрелить запросом ещё раз.
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

    // Тело 429 без счётчиков и порогов: сказать вызывающему, сколько попыток осталось, — научить
    // его держаться прямо под лимитом. Секунды округляются вверх минимум до одной, потому что
    // Retry-After: 0 читается как «повторяй прямо сейчас».
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

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.warn("Malformed JSON or invalid request payload: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Invalid request payload format or parameter value", request);
    }

    // Запрос, не попавший ни в один обработчик. Без этого он падал в handleUnexpected и возвращался
    // как 500 «Unexpected server error» с ERROR в логе — за опечатку в URL. С P1-1 это стало
    // важнее: actuator уехал на свой порт, swagger по умолчанию выключен, и пути actuator и
    // springdoc на основном порту — ровно этот случай, где «не найдено» и есть честный ответ.
    @ExceptionHandler({
            org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNoHandler(Exception ex, HttpServletRequest request) {
        log.debug("No handler for {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "Endpoint not found", request);
    }

    // Верный путь, неверный глагол — GET /api/v1/auth/login по @PostMapping: так делает браузер с
    // вставленным в него адресом API и любой сканер со всем подряд. Тот же дефект, что у
    // handleNoHandler, только про метод: без этого — 500 со стектрейсом, будто виноват сервис.
    // Allow не любезность: RFC 9110 требует его на 405, и только он делает отказ действенным.
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

    // Та же форма ещё раз: тело, которое эндпойнт не умеет читать (form-post в JSON-API, запрос
    // вовсе без Content-Type). Ошибка клиента, поэтому 415 и строка DEBUG, а не 500 со стектрейсом.
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

    // Единственная форма, которую имеет любой ответ об ошибке, с какими бы статусом и заголовками
    // он ни шёл.
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
