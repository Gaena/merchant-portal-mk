package az.millikart.directory.repository;

import az.millikart.directory.domain.Company;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyRepository extends JpaRepository<Company, String> {

    // Фильтр и поиск — в запросе: отсев после чтения даёт короткие страницы и неверный totalElements
    // (P2-1, P3-1). status NOT NULL, так что <> ничего не теряет. Без escape '!' введённый % вернёт
    // всю таблицу. cast у параметра обязателен: null внутри lower() Hibernate шлёт как bytea, и PostgreSQL — 500.
    @Query("""
            select c from Company c
            where c.status <> :excluded
              and (:search is null
                   or lower(c.name) like lower(cast(:search as string)) escape '!'
                   or lower(c.id) like lower(cast(:search as string)) escape '!')
            """)
    Page<Company> search(@Param("excluded") String excluded,
                         @Param("search") String search,
                         Pageable pageable);

    // Удалённые тоже считаются: мягкое удаление логин из уникального индекса не освобождает (Р-93).
    boolean existsByProviderLogin(String providerLogin);

    boolean existsByProviderLoginAndIdNot(String providerLogin, String id);

    // Удалённые тоже: их логин уникальный индекс не освобождает (Р-95).
    @Query("select c.providerLogin from Company c where c.providerLogin is not null")
    List<String> findAllProviderLogins();
}
