package az.millikart.auth.repository;

import az.millikart.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// Пишут двое сразу: ротация токена и отзыв семейства или пользователя. Запросы устроены так, чтобы
// закоммитивший вторым видел работу первого.
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserId(UUID userId);

    // Отзыв зовёт это до массового UPDATE: дожидается встречного refresh и видит выпущенного им наследника.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.familyId = :familyId")
    List<RefreshToken> lockFamily(@Param("familyId") UUID familyId);

    // То же для всех семейств пользователя разом, см. lockFamily.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.userId = :userId")
    List<RefreshToken> lockAllForUser(@Param("userId") UUID userId);

    // Условный UPDATE, а не save прочитанной сущности: 0 строк, если токен успели отозвать, — иначе
    // поверх отзыва лёг бы revoked_at = NULL и вышел наследник мёртвого семейства. COALESCE хранит
    // время первой ротации: иначе повтор в грейсе держит краденый токен живым.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.rotatedAt = COALESCE(t.rotatedAt, :now) "
            + "WHERE t.id = :id AND t.revokedAt IS NULL")
    int markRotatedIfLive(@Param("id") UUID id, @Param("now") Instant now);

    // Массовый UPDATE: отзыв не зависит от того, какие строки загружены в сессию; clearAutomatically
    // выбрасывает строки, поднятые lockFamily.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    // Гигиена: просроченный токен и так отвергается при refresh.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    int deleteAllExpiredBefore(@Param("now") Instant now);
}
