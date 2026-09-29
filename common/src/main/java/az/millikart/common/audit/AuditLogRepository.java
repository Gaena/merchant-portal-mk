package az.millikart.common.audit;

import java.util.UUID;
import org.springframework.data.repository.Repository;

// Только дозапись: пустой Repository, а не CrudRepository, — delete и массовых update не существует.
// Не расширять: журнал, который правит аудируемое приложение, ничего не доказывает. Права в БД — Р-42.
@org.springframework.stereotype.Repository
public interface AuditLogRepository extends Repository<AuditLog, UUID> {

    AuditLog save(AuditLog auditLog);
}
