package az.millikart.directory.repository;

import az.millikart.directory.domain.Terminal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalRepository extends JpaRepository<Terminal, Integer> {

    // companyId — скоуп, а не поиск: null — глобальный читатель (P3-1). left join — терминал без
    // компании остаётся в списке. Без escape '!' введённый % вернёт всю таблицу. cast у параметра обязателен:
    // null внутри lower() Hibernate шлёт как bytea, и PostgreSQL — 500.
    @Query("""
            select t from Terminal t
            left join Company c on c.id = t.companyId
            where (:companyId is null or t.companyId = :companyId)
              and (:restricted = false or t.id in :terminalIds)
              and (:search is null
                   or lower(t.name) like lower(cast(:search as string)) escape '!'
                   or lower(t.login) like lower(cast(:search as string)) escape '!'
                   or cast(t.id as string) like lower(cast(:search as string)) escape '!'
                   or lower(t.companyId) like lower(cast(:search as string)) escape '!'
                   or lower(c.name) like lower(cast(:search as string)) escape '!')
            """)
    // restricted — скоуп сотрудника (Р-131): только terminalIds. Список связывается и без него — IN () невалиден.
    Page<Terminal> search(@Param("companyId") String companyId,
                          @Param("restricted") boolean restricted,
                          @Param("terminalIds") java.util.Collection<Integer> terminalIds,
                          @Param("search") String search,
                          Pageable pageable);

    List<Terminal> findAllByOrderByNameAscIdAsc();

    List<Terminal> findAllByCompanyIdOrderByNameAscIdAsc(String companyId);

    Optional<Terminal> findByMerchantRid(String merchantRid);

    @Query("select t.merchantRid from Terminal t where t.merchantRid is not null")
    List<String> findAllMerchantRids();

    // Явно, а не @GeneratedValue: иначе save заменил бы заданный номер терминала новым (Р-81).
    @Query(value = "SELECT nextval('terminals_id_seq')", nativeQuery = true)
    long nextId();
}
