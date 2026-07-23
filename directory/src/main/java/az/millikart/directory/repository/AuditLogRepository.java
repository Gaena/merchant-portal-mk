package az.millikart.directory.repository;

import az.millikart.directory.domain.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findAllByEntityTypeAndEntityId(String entityType, String entityId);
    List<AuditLog> findAllByCompanyId(String companyId);
}
