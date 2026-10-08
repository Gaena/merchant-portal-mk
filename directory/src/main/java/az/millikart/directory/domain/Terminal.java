package az.millikart.directory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "terminals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Terminal {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    // Сверка и ручной PATCH пишут строку целиком: без версии поздний затирал ранний (TERMINAL-LOST-UPDATE).
    // null у нового терминала — save его вставляет, а не сливает по заданному номеру (Р-81).
    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "name", nullable = false)
    private String name;

    // «TerminalSys/…». К провайдеру с ним не ходят (Р-93), выписка не читает (Р-97): только подпись
    // терминала без номера.
    @Column(name = "login", nullable = false)
    private String login;

    @Column(name = "company_id")
    private String companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private TerminalStatus status = TerminalStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_source", nullable = false, length = 16)
    @Builder.Default
    private TerminalStatusSource statusSource = TerminalStatusSource.MANUAL;

    // Мерчант у провайдера (merchant.rid), не путать с ridByMerchant платежа в pbl (Р-69). По нему
    // сверка находит терминал в слепке; пусто — сверка терминал не трогает.
    @Column(name = "merchant_rid")
    private String merchantRid;

    // Номер терминала у провайдера (terminal.rid): с ним pbl создаёт заказ (Р-96). Пусто — платежей
    // терминал не принимает.
    @Column(name = "terminal_rid")
    private String terminalRid;

    // Разрешены ли DMS-ссылки (Р-132); меняет только SYSTEM_ADMIN, проверяет pbl при создании ссылки.
    @Column(name = "dms_allowed", nullable = false)
    @Builder.Default
    private boolean dmsAllowed = true;

    @Column(name = "created_by")
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
