package az.millikart.pbl.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

/**
 * Configuration and metadata for a generated payment link.
 */
@Entity
@Table(name = "payment_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentLink {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Optimistic-locking version, guards concurrent payment/count updates. */
    @Version
    @Column(name = "version")
    private Long version;

    /** Reference ID from the provider (corresponds to {@code rid}). */
    @Column(name = "provider_reference")
    private String providerReference;

    /** Reference ID from the merchant side. */
    @Column(name = "merchant_order_id")
    private String merchantOrderId;

    @Column(name = "terminal_id", nullable = false)
    private Integer terminalId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    /** ISO 4217 currency code (e.g., AZN). */
    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "description")
    private String description;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false)
    private UsageType usageType;

    /** Allowed number of payments (for {@code MULTIPLE}). */
    @Column(name = "max_payments")
    private Integer maxPayments;

    /** Number of successful payments processed. */
    @Column(name = "current_payments_count", nullable = false)
    private Integer currentPaymentsCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentLinkStatus status;

    /** Custom metadata for the merchant. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;


    @OneToMany(mappedBy = "link", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();
}
