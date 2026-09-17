package az.millikart.pbl.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Подтверждённый эквайером возврат — строкой, со временем возврата (Р-89). Пишется в той же
// транзакции, что и рост refunded_amount; свидетельство для спора по-прежнему лежит в
// provider_response.mpRefunds. Сводка главной вычитает возвраты по refundedAt, а не по дате платежа.
@Entity
@Table(name = "transaction_refunds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionRefund {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private Transaction transaction;

    @Column(name = "amount", nullable = false, updatable = false)
    private BigDecimal amount;

    // Тот же момент, что в mpRefunds[i].at: берётся один раз и пишется в оба места.
    @Column(name = "refunded_at", nullable = false, updatable = false)
    private Instant refundedAt;

    @Column(name = "rid_by_pmo", updatable = false)
    private String ridByPmo;
}
