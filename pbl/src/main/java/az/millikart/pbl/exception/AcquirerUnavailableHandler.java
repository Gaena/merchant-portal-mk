package az.millikart.pbl.exception;

import az.millikart.common.dto.ErrorResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Открытый breaker: вызов не ушёл и денег не двигал — 503, а не 500 (Р-103). Здесь, а не в common:
// Resilience4j есть только в pbl. Впереди GlobalExceptionHandler — иначе исключение заберёт handleUnexpected.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AcquirerUnavailableHandler {

    private static final Logger log = LoggerFactory.getLogger(AcquirerUnavailableHandler.class);

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleOpenCircuit(CallNotPermittedException ex, HttpServletRequest request) {
        log.warn("Refused {} {}: the circuit breaker to the acquirer is open", request.getMethod(), request.getRequestURI());
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                "The acquirer is temporarily unavailable; nothing was sent to it. Try again in a minute.",
                request.getRequestURI()));
    }
}
