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

    /**
     * Reference id **платежа**, который задаём мы как мерчант, — словарь провайдера:
     *
     *   merchantRid   — reference id мерчанта, его задаёт провайдер (у нас он в терминалах);
     *   ridByMerchant — reference id платежа, его задаёт мерчант.
     *
     * Уходит провайдеру полем `ridByMerchant` при заведении заказа и служит ключом страницы
     * возврата плательщика. Случайный UUID на каждую попытку оплаты, поэтому not null: платежа,
     * который мы завели без него, не бывает. Пустым он приходит только из базы провайдера, где
     * заказ заводили не мы, — это про `ecom`.
     *
     * Колонка называлась `merchant_rid` и обещала совсем другой идентификатор (009).
     */
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

    // Сколько реально списано с карты: частичное списание делает это меньше amount, а amount
    // остаётся авторизованной суммой — записью о том, сколько держали. @Builder.Default намеренно
    // нет: null значит «списания не было» — нормальное состояние любого SMS-платежа, и потолок
    // возврата читает его именно так (PaymentLinkService.refundableBase).
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
}
