package az.millikart.ecom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Возврат или списание заказа выписки, исход которого ещё не записан (Р-124) — как MoneyOperationAttempt в pbl.
@Entity
@Table(name = "provider_order_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderOrderAttempt {

    // Живой вызов идёт секунды (таймаут чтения клиента — 10 с); «идёт» дольше — сервис упал посреди вызова.
    public static final Duration STALE_AFTER = Duration.ofMinutes(5);

    public enum Kind { CAPTURE, REFUND }

    public enum State { IN_PROGRESS, UNKNOWN }

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private String orderId;

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

    public boolean outcomeUnknown(Instant now) {
        return state == State.UNKNOWN || startedAt.plus(STALE_AFTER).isBefore(now);
    }
}
