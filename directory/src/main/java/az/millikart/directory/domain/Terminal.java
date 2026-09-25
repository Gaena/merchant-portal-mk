package az.millikart.directory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    @Column(name = "name", nullable = false)
    private String name;

    // Логин терминала у провайдера. К провайдеру с ним больше не ходят — только креды компании (Р-93);
    // выписка ecom его тоже не читает (Р-97). Нужен сверке статусов и подписи терминала без номера.
    @Column(name = "login", nullable = false)
    private String login;

    @Column(name = "company_id")
    private String companyId;

    // Никогда не null: колонка not null default 'ACTIVE' (005-terminal-status.xml).
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private TerminalStatus status = TerminalStatus.ACTIVE;

    // Кто поставил текущий статус. Не null по той же причине: колонка not null default 'MANUAL'
    // (006-terminal-status-source.xml), и для всех существующих строк это правда — до появления
    // синхронизации статусы ставили только люди.
    @Enumerated(EnumType.STRING)
    @Column(name = "status_source", nullable = false, length = 16)
    @Builder.Default
    private TerminalStatusSource statusSource = TerminalStatusSource.MANUAL;

    /**
     * Reference id **мерчанта**, который задаёт **провайдер**, — словарь провайдера:
     *
     *   merchantRid   — reference id мерчанта, его задаёт провайдер (это поле);
     *   ridByMerchant — reference id платежа, его задаёт мерчант (`pbl`, в транзакции).
     *
     * У провайдера один терминал — это один мерчант, поэтому здесь и стоит его rid: заполняется
     * при заведении, когда админ выбирает строку из слепка, а логин с названием приходят оттуда
     * же. Пусто у терминалов, заведённых до синхронизации; их сверка не касается.
     */
    @Column(name = "merchant_rid")
    private String merchantRid;

    // Номер терминала у провайдера (terminal.rid): с ним pbl создаёт заказ (Р-96). Из справочника при
    // заведении, дальше его ведёт сверка. Пусто у терминалов, заведённых до Р-96 без справочника.
    @Column(name = "terminal_rid")
    private String terminalRid;

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
