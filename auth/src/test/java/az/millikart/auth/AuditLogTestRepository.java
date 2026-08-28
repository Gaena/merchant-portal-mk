package az.millikart.auth;

import az.millikart.common.audit.AuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

// Полный доступ к audit_logs — только для тестов. Приложению так нельзя: AuditLogRepository
// в common отдаёт лишь save, а у AuditLog нет сеттеров, потому что журнал append-only (Р-42).
// Тестам же надо чистить таблицу между прогонами и читать записанное, поэтому лазейка лежит здесь,
// в тестовых исходниках. Подхватывается: EnableJpaRepositories указывает на пакет сервиса.
public interface AuditLogTestRepository extends JpaRepository<AuditLog, UUID> {
}
