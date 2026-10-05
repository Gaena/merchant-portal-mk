package az.millikart.pbl.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Возврат или списание, исход которого ещё не записан (Р-123). Живёт от отправки эквайеру до записи итога.
@Entity
@Table(name = "money_operation_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoneyOperationAttempt {

    // Живой вызов идёт секунды (таймаут чтения клиента — 10 с); «идёт» дольше — сервис упал посреди вызова.
    public static final Duration STALE_AFTER = Duration.ofMinutes(5);

    public enum Kind { CAPTURE, REFUND }

    public enum State { IN_PROGRESS, UNKNOWN }

    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private Kind kind;

    @Column(name = "amount", nullable = false, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private State state;

    @Column(name = "started_by", nullable = false, updatable = false)
    private String startedBy;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    // Исход неизвестен: эквайер не ответил или строка «идёт» дольше STALE_AFTER.
    public boolean outcomeUnknown(Instant now) {
        return state == State.UNKNOWN || startedAt.plus(STALE_AFTER).isBefore(now);
    }
}
