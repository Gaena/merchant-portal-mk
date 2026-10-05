package az.millikart.auth.repository;

import az.millikart.auth.domain.User;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    List<User> findByStatusAndLastActivityAtBefore(String status, Instant threshold);

    // FOR UPDATE: без блокировки параллельные попытки теряют приращения счётчика неудач, и локаут
    // Р-28 наступает позже шестой. Держится на время BCrypt одного входа; вторую попытку в тот же логин сюда
    // не пускает LoginRateLimiter, чтобы она не ждала замок с соединением пула (Р-118).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findForLoginByUsername(@Param("username") String username);

    // Нативный SQL: у User нет связи с Company (долг общей базы, AGENTS.md §10). LEFT JOIN — иначе
    // выпадет админ без компании. id в ORDER BY — уникальный тайбрейкер (P2-1). Каждый LIKE — с
    // ESCAPE, иначе введённый % вернёт всю таблицу (P3-1).
    @Query(value = """
            SELECT u.* FROM users u
            LEFT JOIN companies c ON c.id = u.company_id
            WHERE u.status <> 'DELETED'
              AND (:companyId IS NULL OR u.company_id = :companyId)
              AND (:role IS NULL OR u.role = :role)
              AND (:search IS NULL
                   OR lower(u.username) LIKE lower(:search) ESCAPE '!'
                   OR lower(u.full_name) LIKE lower(:search) ESCAPE '!'
                   OR lower(u.company_id) LIKE lower(:search) ESCAPE '!'
                   OR lower(c.name) LIKE lower(:search) ESCAPE '!')
            ORDER BY u.username , u.id
            """,
            countQuery = """
            SELECT count(*) FROM users u
            LEFT JOIN companies c ON c.id = u.company_id
            WHERE u.status <> 'DELETED'
              AND (:companyId IS NULL OR u.company_id = :companyId)
              AND (:role IS NULL OR u.role = :role)
              AND (:search IS NULL
                   OR lower(u.username) LIKE lower(:search) ESCAPE '!'
                   OR lower(u.full_name) LIKE lower(:search) ESCAPE '!'
                   OR lower(u.company_id) LIKE lower(:search) ESCAPE '!'
                   OR lower(c.name) LIKE lower(:search) ESCAPE '!')
            """,
            nativeQuery = true)
    Page<User> search(@Param("companyId") String companyId,
                      @Param("role") String role,
                      @Param("search") String search,
                      Pageable pageable);
}
