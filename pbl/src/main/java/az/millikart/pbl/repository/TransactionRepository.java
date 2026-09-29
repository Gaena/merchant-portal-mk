package az.millikart.pbl.repository;

import az.millikart.pbl.domain.Transaction;
import az.millikart.pbl.domain.TransactionStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByProviderOrderId(String providerOrderId);

    // Скаляр, а не сущность: денежная операция сначала берёт блокировку ссылки, потом читает транзакцию.
    // Загруженная до блокировки сущность осталась бы в persistence context старой.
    @Query("SELECT t.link.id FROM Transaction t WHERE t.id = :id")
    Optional<UUID> findLinkIdById(@Param("id") UUID id);

    // Ключ публичной страницы возврата: случайный ridByMerchant, в отличие от providerOrderId, не
    // перебрать. Граф — ленивый link, из которого чек.
    @EntityGraph(attributePaths = "link")
    Optional<Transaction> findByRidByMerchant(UUID ridByMerchant);

    // Использования ссылки считаются только здесь и набором PAID_STATUSES (Р-49); AUTHORIZED под слоты
    // добавляет OpenLinkService (P1-6). countByLinkIdAndStatus не заводить (P2-16): с ним возврат
    // освобождал бы слот.
    long countByLinkIdAndStatusIn(UUID linkId, Collection<TransactionStatus> statuses);

    boolean existsByLinkIdAndStatusIn(UUID linkId, Collection<TransactionStatus> statuses);
    Optional<Transaction> findFirstByLinkIdAndStatusInOrderByCreatedAtDesc(UUID linkId, Collection<TransactionStatus> statuses);
    java.util.List<Transaction> findByLinkIdOrderByCreatedAtDesc(UUID linkId);

    // Граф на link: маппер трогает его на каждой строке страницы, иначе N+1.
    @EntityGraph(attributePaths = "link")
    Page<Transaction> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "link")
    Page<Transaction> findByLink_TerminalIdIn(Collection<Integer> terminalIds, Pageable pageable);

    // Пакет сверки, старые первыми. Окно (P1-8a): createdAfter = now - give-up-age, createdBefore =
    // now - min-age; строки старше окна живы, но автоматика их не трогает.
    @EntityGraph(attributePaths = "link")
    List<Transaction> findByStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
            TransactionStatus status, Instant createdAfter, Instant createdBefore, Pageable pageable);

    // [linkId, maxCreatedAt] одним запросом на страницу ссылок, а не на строку (P2-15).
    // С пустой коллекцией не вызывать — IN () невалидный SQL.
    @Query("""
            SELECT t.link.id, MAX(t.createdAt) FROM Transaction t
            WHERE t.link.id IN :linkIds AND t.status IN :statuses
            GROUP BY t.link.id
            """)
    List<Object[]> findLastPaidAtByLinkIds(@Param("linkIds") Collection<UUID> linkIds,
                                           @Param("statuses") Collection<TransactionStatus> statuses);

    // Отчёт сверки о PENDING старше give-up-age: куча должна быть видна, а не расти молча.
    long countByStatusAndCreatedAtBefore(TransactionStatus status, Instant createdBefore);

    // Скалярный запрос намеренно: сверка коммитит строки в своих транзакциях, и выборка сущностей
    // вернула бы протухшие копии из persistence context.
    long countByIdInAndStatus(Collection<UUID> ids, TransactionStatus status);
}
