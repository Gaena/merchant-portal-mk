package az.millikart.common.exception;

import lombok.Getter;

import java.time.Duration;

// Отказ из-за слишком частых обращений — HTTP 429 с заголовком Retry-After. Сообщение намеренно
// неинформативно: говорит, что попытка отклонена, но не сколько попыток осталось и каков порог.
// Единственное число, законно нужное клиенту, несёт getRetryAfter() — когда пробовать снова.
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

}
