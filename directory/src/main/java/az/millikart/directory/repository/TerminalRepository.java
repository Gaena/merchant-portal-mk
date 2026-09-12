package az.millikart.directory.repository;

import az.millikart.directory.domain.Terminal;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalRepository extends JpaRepository<Terminal, Integer> {

    // companyId — скоуп, а не поле поиска: null значит глобального читателя, иначе и страница,
    // и поиск заперты в этой компании (P3-1). left join: терминал без компании обязан остаться
    // в списке. LIKE только с escape '!' — иначе введённый пользователем % вернёт всю таблицу;
    // шаблон готовит SearchTerms.toLikePattern. Индекса под LIKE '%…%' нет намеренно.
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

    // Порядок задан здесь, а не вызывающим, чтобы выпадашка была стабильна; заблокированные
    // терминалы включены намеренно — экран транзакций разрешает по этому списку имена терминалов
    // старых платежей (Р-45).
    List<Terminal> findAllByOrderByNameAscIdAsc();

    List<Terminal> findAllByCompanyIdOrderByNameAscIdAsc(String companyId);

    /** Один терминал провайдера — одна наша компания: связь проверяется перед заведением. */
    Optional<Terminal> findByMerchantRid(String merchantRid);
}
