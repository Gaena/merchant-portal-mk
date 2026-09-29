package az.millikart.common.exception;

import lombok.Getter;

import java.time.Duration;

// HTTP 429 с Retry-After. Сообщение без счётчиков и порогов намеренно: клиенту нужно только retryAfter.
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

}
