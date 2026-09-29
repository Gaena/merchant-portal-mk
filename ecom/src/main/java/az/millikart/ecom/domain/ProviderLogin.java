package az.millikart.ecom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Связь логина мультимерчанта провайдера с мерчантом, какой её видела последняя синхронизация (Р-94).
// У логина без связей — одна строка с пустым мерчантом. Статусы — как у провайдера, без толкования.
@Entity
@Table(name = "provider_logins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderLogin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "login", nullable = false)
    private String login;

    @Column(name = "login_status")
    private String loginStatus;

    @Column(name = "link_status")
    private String linkStatus;

    @Column(name = "merchant_rid")
    private String merchantRid;

    @Column(name = "merchant_title")
    private String merchantTitle;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;
}
