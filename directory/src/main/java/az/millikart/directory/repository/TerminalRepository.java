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
    // компании остаётся в списке. Без escape '!' введённый % вернёт всю таблицу.
    @Query("""
            select t from Terminal t
            left join Company c on c.id = t.companyId
            where (:companyId is null or t.companyId = :companyId)
              and (:search is null
                   or lower(t.name) like :search escape '!'
                   or lower(t.login) like :search escape '!'
                   or cast(t.id as string) like :search escape '!'
                   or lower(t.companyId) like :search escape '!'
                   or lower(c.name) like :search escape '!')
            """)
    Page<Terminal> search(@Param("companyId") String companyId,
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
