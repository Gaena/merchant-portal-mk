package az.millikart.pbl.repository;

import az.millikart.pbl.domain.MoneyOperationAttempt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoneyOperationAttemptRepository extends JpaRepository<MoneyOperationAttempt, UUID> {
}
