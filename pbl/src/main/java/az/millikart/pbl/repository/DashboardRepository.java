package az.millikart.pbl.repository;

import az.millikart.pbl.domain.Transaction;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.pbl.domain.UsageType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// Статистика оплат по ссылкам (P3-7, Р-91). Пояса в SQL нет намеренно: created_at без пояса (UTC на
// PostgreSQL, часы JVM на H2), и сутки из SQL были бы верны на одном движке. База режет по часам, сутки
// и час в поясе отчёта считает DashboardService.foldBuckets.
@org.springframework.stereotype.Repository
public interface DashboardRepository extends Repository<Transaction, UUID> {

    // Одна группировка на все итоги — суммы по дням сходятся с итогом. Денежных CASE нет: целый литерал
    // в CASE роняет копейки, PAID_STATUSES сворачивает сервис. При :unscoped список :terminalIds всё
    // равно связывается — сервис передаёт несуществующий id.
    @Query("""
            SELECT MIN(t.createdAt),
                   pl.currency,
                   t.status,
                   COUNT(t),
                   SUM(COALESCE(t.capturedAmount, t.amount))
            FROM Transaction t JOIN t.link pl
            WHERE t.createdAt >= :from AND t.createdAt < :to
              AND (:unscoped = TRUE OR pl.terminalId IN :terminalIds)
            GROUP BY CAST(t.createdAt AS LocalDate), EXTRACT(HOUR FROM t.createdAt),
                     pl.currency, t.status
            """)
    List<Object[]> aggregateByHourBucket(@Param("from") Instant from,
                                         @Param("to") Instant to,
                                         @Param("unscoped") boolean unscoped,
                                         @Param("terminalIds") Collection<Integer> terminalIds);

    // [terminalId, валюта, статус, число, сумма]. Имени нет: у PaymentLink голый terminal_id, имена
    // сервис добирает одним findAllById по готовому топу.
    @Query("""
            SELECT pl.terminalId,
                   pl.currency,
                   t.status,
                   COUNT(t),
                   SUM(COALESCE(t.capturedAmount, t.amount))
            FROM Transaction t JOIN t.link pl
            WHERE t.createdAt >= :from AND t.createdAt < :to
              AND (:unscoped = TRUE OR pl.terminalId IN :terminalIds)
            GROUP BY pl.terminalId, pl.currency, t.status
            """)
    List<Object[]> aggregateByTerminal(@Param("from") Instant from,
                                       @Param("to") Instant to,
                                       @Param("unscoped") boolean unscoped,
                                       @Param("terminalIds") Collection<Integer> terminalIds);

    // [начало корзины, валюта, сумма] — возвраты, проведённые в окне, по времени возврата (Р-89):
    // возврат по старому платежу уменьшает сегодняшнюю выручку. Корзины часовые — см. заголовок.
    @Query("""
            SELECT MIN(r.refundedAt),
                   pl.currency,
                   SUM(r.amount)
            FROM TransactionRefund r JOIN r.transaction t JOIN t.link pl
            WHERE r.refundedAt >= :from AND r.refundedAt < :to
              AND (:unscoped = TRUE OR pl.terminalId IN :terminalIds)
            GROUP BY CAST(r.refundedAt AS LocalDate), EXTRACT(HOUR FROM r.refundedAt), pl.currency
            """)
    List<Object[]> refundsByHourBucket(@Param("from") Instant from,
                                       @Param("to") Instant to,
                                       @Param("unscoped") boolean unscoped,
                                       @Param("terminalIds") Collection<Integer> terminalIds);

    // Р-89: [terminalId, валюта, сумма возвратов] — те же возвраты окна по терминалам.
    @Query("""
            SELECT pl.terminalId,
                   pl.currency,
                   SUM(r.amount)
            FROM TransactionRefund r JOIN r.transaction t JOIN t.link pl
            WHERE r.refundedAt >= :from AND r.refundedAt < :to
              AND (:unscoped = TRUE OR pl.terminalId IN :terminalIds)
            GROUP BY pl.terminalId, pl.currency
            """)
    List<Object[]> refundsByTerminal(@Param("from") Instant from,
                                     @Param("to") Instant to,
                                     @Param("unscoped") boolean unscoped,
                                     @Param("terminalIds") Collection<Integer> terminalIds);

    // Р-128: [создано, открыто, начата оплата, оплачено] по ссылкам, созданным в окне, — когорта: попытки
    // считаются любые, и после окна тоже. Открыта — есть попытка: её заводит только открытие, дошедшее до
    // провайдера. Оплата начата, если карта отправлена или деньги взяты: старые строки могли не попасть
    // под разметку pbl/016. Пустое окно даёт null в суммах.
    @Query("""
            SELECT COUNT(pl),
                   SUM(CASE WHEN EXISTS (SELECT 1 FROM Transaction opened WHERE opened.link = pl)
                            THEN 1 ELSE 0 END),
                   SUM(CASE WHEN EXISTS (SELECT 1 FROM Transaction started WHERE started.link = pl
                                         AND (started.cardSubmitted = TRUE OR started.status IN :paidStatuses))
                            THEN 1 ELSE 0 END),
                   SUM(CASE WHEN EXISTS (SELECT 1 FROM Transaction paid WHERE paid.link = pl
                                         AND paid.status IN :paidStatuses)
                            THEN 1 ELSE 0 END)
            FROM PaymentLink pl
            WHERE pl.createdAt >= :from AND pl.createdAt < :to
              AND (:unscoped = TRUE OR pl.terminalId IN :terminalIds)
            """)
    List<Object[]> linkFunnel(@Param("from") Instant from,
                              @Param("to") Instant to,
                              @Param("unscoped") boolean unscoped,
                              @Param("terminalIds") Collection<Integer> terminalIds,
                              @Param("paidStatuses") Collection<TransactionStatus> paidStatuses);

    // Р-128: [создание ссылки, начало оплаченной попытки] по ссылкам типа :usageType из окна. Момента
    // оплаты у нас нет — начало оплаченной попытки отстаёт от него на минуты сессии плательщика.
    @Query("""
            SELECT pl.createdAt, MIN(t.createdAt)
            FROM Transaction t JOIN t.link pl
            WHERE pl.usageType = :usageType
              AND t.status IN :paidStatuses
              AND pl.createdAt >= :from AND pl.createdAt < :to
              AND (:unscoped = TRUE OR pl.terminalId IN :terminalIds)
            GROUP BY pl.id, pl.createdAt
            """)
    List<Object[]> paidLinkTimes(@Param("from") Instant from,
                                 @Param("to") Instant to,
                                 @Param("unscoped") boolean unscoped,
                                 @Param("terminalIds") Collection<Integer> terminalIds,
                                 @Param("usageType") UsageType usageType,
                                 @Param("paidStatuses") Collection<TransactionStatus> paidStatuses);

    // [статус ссылки, тип платежа, тип использования, число ссылок]. Одна группировка на три
    // разбиения: комбинаций не больше двух десятков, сворачивает их сервис.
    @Query("""
            SELECT pl.status, pl.paymentType, pl.usageType, COUNT(pl)
            FROM PaymentLink pl
            WHERE pl.createdAt >= :from AND pl.createdAt < :to
              AND (:unscoped = TRUE OR pl.terminalId IN :terminalIds)
            GROUP BY pl.status, pl.paymentType, pl.usageType
            """)
    List<Object[]> aggregateLinks(@Param("from") Instant from,
                                  @Param("to") Instant to,
                                  @Param("unscoped") boolean unscoped,
                                  @Param("terminalIds") Collection<Integer> terminalIds);
}
