package az.millikart.pbl.repository;

import az.millikart.pbl.domain.Transaction;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// Сводка главной страницы (P3-7). Только чтение: пустой маркер Repository, без JpaRepository —
// дашборд ничего не пишет.
//
// **Пояса в запросах нет вообще, и это главное решение этого файла.** Соглашение о хранении
// времени у двух движков разное: на PostgreSQL created_at — timestamp **with** time zone, на H2
// тестов — без пояса, и Hibernate кладёт туда стенные часы JVM. Любое приведение к суткам
// средствами SQL зависело бы то от пояса сессии, то от пояса JVM, и верное на одном движке было
// бы неверным на другом — тесты на H2 такой дефект не видят (`AGENTS.md` §10).
//
// Поэтому база только **дробит** строки не крупнее часа, а сутки и час корзины в поясе отчёта
// вычисляет сервис из MIN(createdAt) — момент читается тем же преобразованием, что и любое
// чтение колонки, и потому верен на обоих движках. Часовые корзины не пересекают полночь ни
// одного пояса со смещением, кратным часу (`DashboardService.foldBuckets`).
//
// :unscoped — глобальный читатель (SYSTEM_ADMIN, AUDITOR); при нём :terminalIds не участвует
// в отборе, но связать его всё равно надо, поэтому сервис передаёт заведомо несуществующий id.
// Пустым список не бывает: у компании без терминалов сервис отвечает нулями, не спрашивая базу.
@org.springframework.stereotype.Repository
public interface DashboardRepository extends Repository<Transaction, UUID> {

    // [самый ранний момент корзины, валюта, статус, число операций, сумма списанного,
    // сумма возвратов]. Одна группировка на итоги, разбивку по статусам, посуточную выручку
    // и распределение по часам: четыре независимых запроса могли бы разойтись между собой,
    // и сумма по дням не сошлась бы с итогом.
    //
    // coalesce(capturedAmount, amount): captured_amount равен null у всех SMS-платежей (списания
    // не было) и меньше amount после частичного списания — брать amount значило бы показать
    // авторизованную сумму как полученные деньги.
    //
    // Денежных CASE здесь нет намеренно: смешение BigDecimal с целым литералом в ветках CASE
    // молча роняет копейки. Суммы считаются по всем статусам, а по PAID_STATUSES сворачиваются
    // уже в сервисе — лишние отброшены, ни одна не округлена.
    @Query("""
            SELECT MIN(t.createdAt),
                   pl.currency,
                   t.status,
                   COUNT(t),
                   SUM(COALESCE(t.capturedAmount, t.amount)),
                   SUM(t.refundedAmount)
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

    // [terminalId, валюта, статус, число операций, сумма списанного, сумма возвратов].
    // Имя терминала не джойнится: Terminal — отдельная сущность, ссылки на неё у PaymentLink нет
    // (там голый terminal_id), поэтому имена сервис добирает одним findAllById по готовому топу.
    @Query("""
            SELECT pl.terminalId,
                   pl.currency,
                   t.status,
                   COUNT(t),
                   SUM(COALESCE(t.capturedAmount, t.amount)),
                   SUM(t.refundedAmount)
            FROM Transaction t JOIN t.link pl
            WHERE t.createdAt >= :from AND t.createdAt < :to
              AND (:unscoped = TRUE OR pl.terminalId IN :terminalIds)
            GROUP BY pl.terminalId, pl.currency, t.status
            """)
    List<Object[]> aggregateByTerminal(@Param("from") Instant from,
                                       @Param("to") Instant to,
                                       @Param("unscoped") boolean unscoped,
                                       @Param("terminalIds") Collection<Integer> terminalIds);

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
