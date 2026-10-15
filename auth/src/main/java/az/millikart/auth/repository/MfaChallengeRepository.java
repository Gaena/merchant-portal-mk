package az.millikart.auth.repository;

import az.millikart.auth.domain.MfaChallenge;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, UUID> {

    Optional<MfaChallenge> findByTokenHash(String tokenHash);

    // Условный UPDATE: 0 строк — шаг уже израсходован или истёк, второй код по нему сессии не даёт. Без
    // clearAutomatically: правки пользователя в той же транзакции остаются в сессии и сохраняются.
    @Modifying(flushAutomatically = true)
    @Query("UPDATE MfaChallenge c SET c.usedAt = :now WHERE c.id = :id AND c.usedAt IS NULL AND c.expiresAt > :now")
    int markUsedIfLive(@Param("id") UUID id, @Param("now") Instant now);

    // Сброс фактора и смена пароля: начатый до них вход не доводится до сессии.
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM MfaChallenge c WHERE c.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);

    // Гигиена: истёкший шаг и так отвергается.
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM MfaChallenge c WHERE c.expiresAt < :now")
    int deleteAllExpiredBefore(@Param("now") Instant now);
}
