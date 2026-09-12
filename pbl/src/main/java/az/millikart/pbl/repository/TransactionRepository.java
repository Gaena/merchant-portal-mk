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

    // Ключ страницы возврата плательщика: ridByMerchant — случайный UUID на попытку оплаты, его, в
    // отличие от providerOrderId, не перебрать. Граф подтягивает ленивый link, из которого чек.
    @EntityGraph(attributePaths = "link")
    Optional<Transaction> findByRidByMerchant(UUID ridByMerchant);

    // Счёт использований ссылки идёт только отсюда и только набором TransactionStatus.PAID_STATUSES
    // (возвращённый платёж входит); OpenLinkService добавляет AUTHORIZED под слоты (P1-6). Одного
    // countByLinkIdAndStatus нет намеренно (P2-16, Р-49): вернёшь его — возврат снова начнёт
    // освобождать слот. Обслуживает idx_transactions_link_status (link_id, status) из P2-3.
    long countByLinkIdAndStatusIn(UUID linkId, Collection<TransactionStatus> statuses);

    // Правка суммы отвергается, как только деньги были в игре (P2-9). Именно существование, а не
    // загрузка попыток: ответ — один boolean, а на живой ссылке строк много.
    boolean existsByLinkIdAndStatusIn(UUID linkId, Collection<TransactionStatus> statuses);
    Optional<Transaction> findFirstByLinkIdAndStatusInOrderByCreatedAtDesc(UUID linkId, Collection<TransactionStatus> statuses);
    java.util.List<Transaction> findByLinkIdOrderByCreatedAtDesc(UUID linkId);

    // Граф подтягивает ленивый link: маппер ответа трогает его на каждой строке страницы.
    @EntityGraph(attributePaths = "link")
    Page<Transaction> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "link")
    Page<Transaction> findByLink_TerminalIdIn(Collection<Integer> terminalIds, Pageable pageable);

    // Пакет для фоновой сверки, старые первыми: завал разбирается в порядке поступления за
    // несколько проходов. Границы с двух сторон (P1-8a): createdAfter = now - give-up-age,
    // createdBefore = now - min-age; строки старше нижней границы живы, но автоматика их уже не
    // трогает. Обслуживает idx_transactions_status_created (status, created_at) из P2-3.
    @EntityGraph(attributePaths = "link")
    List<Transaction> findByStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
            TransactionStatus status, Instant createdAfter, Instant createdBefore, Pageable pageable);

    // Время последней оплаты на всю страницу ссылок одним запросом (P2-15): вызов на строку — это
    // двадцать запросов на листинг. Пары приходят как [linkId, maxCreatedAt] и сшиваются в памяти;
    // обслуживает idx_transactions_link_status (link_id, status) из P2-3.
    // С пустой коллекцией не вызывать — IN () невалидный SQL, вызывающий пропускает запрос сам.
    @Query("""
            SELECT t.link.id, MAX(t.createdAt) FROM Transaction t
            WHERE t.link.id IN :linkIds AND t.status IN :statuses
            GROUP BY t.link.id
            """)
    List<Object[]> findLastPaidAtByLinkIds(@Param("linkIds") Collection<UUID> linkIds,
                                           @Param("statuses") Collection<TransactionStatus> statuses);

    // Сверка отчитывается этим числом о куче PENDING старше give-up-age: она должна быть видна, а
    // не расти молча.
    long countByStatusAndCreatedAtBefore(TransactionStatus status, Instant createdBefore);

    // Пересчёт уже загруженного пакета именно скалярным запросом: сверка коммитит каждую строку
    // своей транзакцией, загруженные сущности к этому моменту протухли, и выборка сущностей
    // вернула бы их же из persistence context.
    long countByIdInAndStatus(Collection<UUID> ids, TransactionStatus status);
}
