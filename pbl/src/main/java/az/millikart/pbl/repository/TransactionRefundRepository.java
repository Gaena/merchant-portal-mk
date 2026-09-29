package az.millikart.pbl.repository;

import az.millikart.pbl.domain.TransactionRefund;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRefundRepository extends JpaRepository<TransactionRefund, UUID> {

    List<TransactionRefund> findByTransactionIdOrderByRefundedAtAsc(UUID transactionId);
}
