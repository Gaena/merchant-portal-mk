package az.millikart.ecom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Ключ — rid мерчанта, одна строка на мерчанта (Р-79). active гасится после нескольких пропаданий
// подряд: один оборванный опрос не должен выключать терминалы, под которыми идут платежи (Р-66).
@Entity
@Table(name = "provider_terminals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderTerminal {

    @Id
    @Column(name = "rid", nullable = false)
    private String rid;

    @Column(name = "title")
    private String title;

    @Column(name = "login")
    private String login;

    // Номер терминала у провайдера (terminal.rid), Р-96.
    @Column(name = "terminal_rid")
    private String terminalRid;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "missing_runs", nullable = false)
    @Builder.Default
    private int missingRuns = 0;

    @Column(name = "first_seen_at", updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "synced_at")
    private Instant syncedAt;
}
