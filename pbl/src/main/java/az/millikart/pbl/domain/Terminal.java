package az.millikart.pbl.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    // К провайдеру ходят с кредами компании, а не с логином терминала (Р-93).
    @Column(name = "login", nullable = false)
    private String login;

    @Column(name = "company_id", nullable = true)
    private String companyId;

    // Номер терминала у провайдера: с ним создаётся заказ (POST /order?terminalRid=…, Р-96). Пишет
    // directory; пусто у терминалов, заведённых до Р-96 без справочника, — платежи по ним 400.
    @Column(name = "terminal_rid")
    private String terminalRid;

    // Пустым не бывает: колонка not null default 'ACTIVE' (005-terminal-status.xml).
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private TerminalStatus status = TerminalStatus.ACTIVE;

    public boolean isBlocked() {
        return status == TerminalStatus.BLOCKED;
    }
}
