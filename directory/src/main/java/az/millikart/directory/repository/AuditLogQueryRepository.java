package az.millikart.directory.repository;

import az.millikart.common.audit.AuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

// Только чтение: пустой маркер Repository плюс JpaSpecificationExecutor, чьи методы все читающие.
// JpaRepository добавлять нельзя — журнал не должен уметь править себя (Р-42). Пишет журнал
// AuditLogRepository из common; предикаты этой стороны собраны в AuditLogQueryService#filter.
@org.springframework.stereotype.Repository
public interface AuditLogQueryRepository
        extends Repository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
}
