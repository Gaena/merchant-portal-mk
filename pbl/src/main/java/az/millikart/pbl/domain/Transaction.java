package az.millikart.pbl.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_id", nullable = false)
    private PaymentLink link;

    // Номер платежа у мерчанта — наш случайный UUID на каждую попытку; уходит провайдеру при заведении
    // заказа и служит ключом страницы возврата. Не путать с merchantRid — мерчантом у провайдера (Р-69).
    @Column(name = "rid_by_merchant", nullable = false)
    private UUID ridByMerchant;

    @Column(name = "provider_order_id", nullable = false)
    private String providerOrderId;

    @Column(name = "provider_password", nullable = false)
    private String providerPassword;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "refunded_amount", nullable = false)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    // Списано с карты; amount остаётся авторизованной суммой (P0-8). @Builder.Default не ставить:
    // null значит «списания не было», и так его читает потолок возврата (refundableBase).
    @Column(name = "captured_amount")
    private BigDecimal capturedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_response")
    private Map<String, Object> providerResponse;

    @Column(name = "client_ip")
    private String clientIp;

    @Column(name = "user_agent")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // Когда сверка последний раз брала строку в пакет (Р-110); пишет только её отметка.
    @Column(name = "last_reconciled_at")
    private Instant lastReconciledAt;
}
