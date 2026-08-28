package az.millikart.pbl;

import az.millikart.common.audit.AuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

// Полный доступ к audit_logs — только для тестов. Приложению так нельзя: AuditLogRepository из
// common отдаёт лишь save, а у AuditLog нет сеттеров, потому что журнал append-only (Р-42).
// Тестам же нужно чистить таблицу и читать записанное, поэтому лазейка лежит в тестовых
// исходниках, откуда её не достанет продовый код; подхватывает её EnableJpaRepositories по пакету.
public interface AuditLogTestRepository extends JpaRepository<AuditLog, UUID> {
}
