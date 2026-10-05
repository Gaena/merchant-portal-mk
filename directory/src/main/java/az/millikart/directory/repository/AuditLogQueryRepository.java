package az.millikart.directory.repository;

import az.millikart.common.audit.AuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;

// Только чтение (Р-42): единственный метод, а не JpaSpecificationExecutor — тот несёт delete(Specification)
// и стёр бы записи журнала. JpaRepository не добавлять по той же причине.
@org.springframework.stereotype.Repository
public interface AuditLogQueryRepository extends Repository<AuditLog, UUID> {

    Page<AuditLog> findAll(Specification<AuditLog> specification, Pageable pageable);
}
