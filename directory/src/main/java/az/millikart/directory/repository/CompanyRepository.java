package az.millikart.directory.repository;

import az.millikart.directory.domain.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyRepository extends JpaRepository<Company, String> {

    // Фильтр статуса и поиск обязаны быть в запросе (P2-1, P3-1): отсев строк после чтения страницы
    // даёт короткие страницы и totalElements, считающий записи, которых вызывающий не видит.
    // status в схеме NOT NULL, поэтому <> ничего не теряет. LIKE только с escape '!' — иначе
    // введённый пользователем % вернёт всю таблицу; индекса под LIKE '%…%' нет намеренно.
    @Query("""
            select c from Company c
            where c.status <> :excluded
              and (:search is null
                   or lower(c.name) like :search escape '!'
                   or lower(c.id) like :search escape '!')
            """)
    Page<Company> search(@Param("excluded") String excluded,
                         @Param("search") String search,
                         Pageable pageable);
}
