package az.millikart.auth.repository;

import az.millikart.auth.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    // Нативный SQL: сущности companies в auth нет — поиск по названию компании читает таблицу
    // модуля directory (осознанный долг общей базы, AGENTS.md §10). LEFT JOIN — иначе из списка
    // выпадет админ без компании. ORDER BY username, id: id — уникальный тайбрейкер (P2-1). Каждый
    // LIKE обязан идти с ESCAPE: без него введённый пользователем % вернёт всю таблицу (P3-1).
    @Query(value = """
            SELECT u.* FROM users u
            LEFT JOIN companies c ON c.id = u.company_id
            WHERE u.status <> 'DELETED'
              AND (:companyId IS NULL OR u.company_id = :companyId)
              AND (:role IS NULL OR u.role = :role)
              AND (:search IS NULL
                   OR lower(u.username) LIKE :search ESCAPE '!'
                   OR lower(u.full_name) LIKE :search ESCAPE '!'
                   OR lower(u.company_id) LIKE :search ESCAPE '!'
                   OR lower(c.name) LIKE :search ESCAPE '!')
            ORDER BY u.username , u.id
            """,
            countQuery = """
            SELECT count(*) FROM users u
            LEFT JOIN companies c ON c.id = u.company_id
            WHERE u.status <> 'DELETED'
              AND (:companyId IS NULL OR u.company_id = :companyId)
              AND (:role IS NULL OR u.role = :role)
              AND (:search IS NULL
                   OR lower(u.username) LIKE :search ESCAPE '!'
                   OR lower(u.full_name) LIKE :search ESCAPE '!'
                   OR lower(u.company_id) LIKE :search ESCAPE '!'
                   OR lower(c.name) LIKE :search ESCAPE '!')
            """,
            nativeQuery = true)
    Page<User> search(@Param("companyId") String companyId,
                      @Param("role") String role,
                      @Param("search") String search,
                      Pageable pageable);
}
