package az.millikart.common.audit;

import java.util.UUID;
import org.springframework.data.repository.Repository;

// Пишущая сторона журнала: один метод, и он только добавляет строки. Наследует пустой маркер
// Repository, а НЕ CrudRepository/JpaRepository, — чтобы delete, deleteAll и массовых update просто
// не существовало; у AuditLog нет сеттеров по той же причине. Интерфейс не расширять: журнал,
// который правит аудируемое им приложение, ничего не доказывает. Права в БД — вторая половина (Р-42).
@org.springframework.stereotype.Repository
public interface AuditLogRepository extends Repository<AuditLog, UUID> {

    AuditLog save(AuditLog auditLog);
}
