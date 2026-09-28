package az.millikart.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Прежний пароль пользователя — BCrypt, как текущий (PCI DSS 8.3.7, Р-102). Держатся три последних:
// вместе с текущим это четыре, которые нельзя повторить.
@Entity
@Table(name = "password_history")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordHistory {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "password_hash", nullable = false, updatable = false)
    private String passwordHash;

    @Column(name = "replaced_at", nullable = false, updatable = false)
    private Instant replacedAt;
}
