package az.millikart.directory.repository;

import az.millikart.common.audit.AuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

// Только чтение: JpaRepository не добавлять — журнал не должен уметь править себя (Р-42).
@org.springframework.stereotype.Repository
public interface AuditLogQueryRepository
        extends Repository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
}
