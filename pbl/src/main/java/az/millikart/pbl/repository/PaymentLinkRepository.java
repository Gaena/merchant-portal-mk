package az.millikart.pbl.repository;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentLinkStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;

@Repository
public interface PaymentLinkRepository extends JpaRepository<PaymentLink, UUID> {

    /**
     * Returns a paginated list of payment links, optionally filtered by terminal and/or status.
     * A {@code null} filter argument is ignored.
     */
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

    @Modifying
    @Query("UPDATE PaymentLink pl SET pl.status = az.millikart.pbl.domain.PaymentLinkStatus.EXPIRED WHERE pl.status = az.millikart.pbl.domain.PaymentLinkStatus.ACTIVE AND pl.expiresAt IS NOT NULL AND pl.expiresAt < :now")
    int expireActiveLinksBefore(@Param("now") java.time.Instant now);
}
