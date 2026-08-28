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

// В таблицу пишут двое сразу: refresh, ротирующий один токен, и отзыв, гасящий семейство или
// пользователя (logout против refresh, блокировка пользователя посреди его refresh). Запросы ниже
// устроены так, чтобы закоммитивший вторым видел работу первого.
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserId(UUID userId);

    // SELECT ... FOR UPDATE до конца транзакции: отзыв зовёт это ДО своего массового UPDATE и
    // ждёт встречный refresh, держащий блокировку строки, — и работает уже со снимком, в котором
    // выпущенный тем refresh наследник есть.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.familyId = :familyId")
    List<RefreshToken> lockFamily(@Param("familyId") UUID familyId);

    // То же для всех семейств пользователя разом, см. lockFamily.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.userId = :userId")
    List<RefreshToken> lockAllForUser(@Param("userId") UUID userId);

    // Условный UPDATE, а не save прочитанной сущности: берёт блокировку строки и не меняет ничего
    // (0 строк), если токен успели отозвать, — иначе поверх отзыва лёг бы устаревший
    // revoked_at = NULL и был бы выдан наследник мёртвого семейства. COALESCE хранит время ПЕРВОЙ
    // ротации: повтор внутри грейса не двигает окно вперёд, иначе краденый токен держат живым.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.rotatedAt = COALESCE(t.rotatedAt, :now) "
            + "WHERE t.id = :id AND t.revokedAt IS NULL")
    int markRotatedIfLive(@Param("id") UUID id, @Param("now") Instant now);

    // Массовый UPDATE намеренно: в семействе может быть много строк, и отзыв не должен зависеть от
    // того, какие из них загружены в сессию. clearAutomatically выбрасывает из контекста строки,
    // поднятые туда lockFamily.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    // Уборка: строки с истёкшим сроком и так отвергаются при поиске.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    int deleteAllExpiredBefore(@Param("now") Instant now);
}
