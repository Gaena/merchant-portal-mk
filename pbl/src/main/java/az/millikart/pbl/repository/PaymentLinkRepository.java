package az.millikart.pbl.repository;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentLinkStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;

@Repository
public interface PaymentLinkRepository extends JpaRepository<PaymentLink, UUID> {

    // FOR UPDATE NOWAIT: сериализует открытие и денежные операции ссылки (P1-5), только в транзакции.
    // NOWAIT (Р-85): таймаут ожидания Hibernate на PostgreSQL не рисует, а очередь держала бы соединения
    // пула, пока держатель ходит к эквайеру. Занятая ссылка — сразу 409.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    Optional<PaymentLink> findWithLockById(UUID id);

    @Query("""
            SELECT pl FROM PaymentLink pl
            WHERE (:terminal IS NULL OR pl.terminalId = :terminal)
              AND (:ignoreAllowedTerminals = true OR pl.terminalId IN :allowedTerminals)
              AND (:status IS NULL OR pl.status = :status)
            """)
    Page<PaymentLink> search(@Param("terminal") Integer terminal,
                             @Param("allowedTerminals") java.util.Collection<Integer> allowedTerminals,
                             @Param("ignoreAllowedTerminals") boolean ignoreAllowedTerminals,
                             @Param("status") PaymentLinkStatus status,
                             Pageable pageable);

    // Версия поднимается: запись, прочитанная до истечения, получит конфликт, а не вернёт ACTIVE.
    @Modifying
    @Query("UPDATE PaymentLink pl SET pl.status = az.millikart.pbl.domain.PaymentLinkStatus.EXPIRED, "
            + "pl.version = COALESCE(pl.version, 0) + 1 "
            + "WHERE pl.status = az.millikart.pbl.domain.PaymentLinkStatus.ACTIVE AND pl.expiresAt IS NOT NULL AND pl.expiresAt < :now")
    int expireActiveLinksBefore(@Param("now") java.time.Instant now);
}
