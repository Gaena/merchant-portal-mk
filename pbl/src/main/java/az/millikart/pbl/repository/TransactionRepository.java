package az.millikart.pbl.repository;

import az.millikart.pbl.domain.Transaction;
import az.millikart.pbl.domain.TransactionStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByProviderOrderId(String providerOrderId);
    long countByLinkIdAndStatus(UUID linkId, TransactionStatus status);
    Optional<Transaction> findFirstByLinkIdAndStatusInOrderByCreatedAtDesc(UUID linkId, Collection<TransactionStatus> statuses);
    java.util.List<Transaction> findByLinkIdOrderByCreatedAtDesc(UUID linkId);
}
