package az.millikart.directory.domain;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    // Креды компании к провайдеру (Р-93). Логин — с префиксом «MultiMerchantSys/» (Р-94); пароль —
    // только шифротекст CredentialCipher. Пусто у компаний, заведённых до Р-93.
    @Column(name = "provider_login")
    private String providerLogin;

    @Column(name = "provider_password")
    private String providerPassword;

    // VÖEN — реквизит продавца на чеке плательщика (Р-129); 10 цифр или пусто.
    @Column(name = "tax_id")
    private String taxId;

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
