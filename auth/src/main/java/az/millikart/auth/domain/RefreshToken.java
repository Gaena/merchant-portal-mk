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
import lombok.Setter;

// Один refresh-токен одной сессии входа (P1-12). В строке нет самого токена — только его SHA-256:
// единственный экземпляр у клиента, сервер токен узнаёт, но воспроизвести не может, поэтому дамп
// таблицы не даёт ни одной живой сессии. Токены одного входа образуют семейство (familyId), и отзыв
// ставится на всё семейство сразу, а не на отдельный токен.
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    // SHA-256 токена, hex — 64 символа. Уникален: это и есть ключ поиска.
    @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    // Когда для токена выпущен наследник. От этого момента отсчитывается грейс ротации.
    @Column(name = "rotated_at")
    private Instant rotatedAt;

    // Явный отзыв: logout, блокировка или удаление пользователя, обнаружен повтор.
    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isRotated() {
        return rotatedAt != null;
    }
}
