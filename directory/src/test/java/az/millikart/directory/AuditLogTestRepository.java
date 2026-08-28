package az.millikart.directory;

import az.millikart.common.audit.AuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

// Полный доступ к audit_logs только для тестов. Приложению так нельзя: AuditLogRepository из common
// отдаёт лишь save, а у AuditLog нет сеттеров — журнал append-only (Р-42). Лазейка живёт в тестовых
// исходниках, откуда до неё не дотянется прод. Подхватывается потому, что EnableJpaRepositories на
// классе приложения указывает пакет сервиса, а тестовые классы лежат в том же пакете.
public interface AuditLogTestRepository extends JpaRepository<AuditLog, UUID> {
}
