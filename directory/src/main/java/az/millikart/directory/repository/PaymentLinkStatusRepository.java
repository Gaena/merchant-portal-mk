package az.millikart.directory.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

// Пишет в payment_links — таблицу модуля pbl: осознанный кросс-модульный долг, брокера сообщений
// нет (Р-39, problems.md §1). PaymentLink не мапится как entity: второй JPA-маппинг чужой таблицы
// молча разойдётся с ней. Каждый метод — один UPDATE в транзакции вызывающего вместе со сменой
// статуса: заблокированного терминала с оплачиваемыми ссылками не должно быть ни мгновения.
@Repository
public class PaymentLinkStatusRepository {

    private static final Logger log = LoggerFactory.getLogger(PaymentLinkStatusRepository.class);

    // Литералы: enum модуля pbl не на classpath этого модуля.
    private static final String ACTIVE = "ACTIVE";
    private static final String SUSPENDED = "SUSPENDED";
    private static final String EXPIRED = "EXPIRED";

    private static final String TABLE = "payment_links";

    @PersistenceContext
    private EntityManager entityManager;

    private final DataSource dataSource;

    public PaymentLinkStatusRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Р-39: приостанавливаются только ACTIVE. EXPIRED, COMPLETED и CANCELED — факты о прошлом
    // ссылки, их не трогать: иначе разблокировке придётся гадать, чем каждая из них была.
    // Возвращает число приостановленных ссылок — оно уходит в запись журнала.
    public int suspendActiveLinks(Integer terminalId) {
        if (linksTableMissing()) {
            return 0;
        }
        return entityManager.createNativeQuery(
                        "UPDATE payment_links SET status = :suspended "
                                + "WHERE terminal_id = :terminalId AND status = :active")
                .setParameter("suspended", SUSPENDED)
                .setParameter("terminalId", terminalId)
                .setParameter("active", ACTIVE)
                .executeUpdate();
    }

    // Разблокировка (Р-40), часть первая: в ACTIVE возвращаются только ссылки, чей срок ещё
    // впереди. NULL в expires_at — «без срока» (ссылки старше P1-9), платёжный путь считает такие
    // живыми, поэтому они возвращаются тоже. Возвращает число восстановленных ссылок.
    public int resumeSuspendedLinks(Integer terminalId, Instant now) {
        if (linksTableMissing()) {
            return 0;
        }
        return entityManager.createNativeQuery(
                        "UPDATE payment_links SET status = :active "
                                + "WHERE terminal_id = :terminalId AND status = :suspended "
                                + "AND (expires_at IS NULL OR expires_at > :now)")
                .setParameter("active", ACTIVE)
                .setParameter("terminalId", terminalId)
                .setParameter("suspended", SUSPENDED)
                .setParameter("now", now)
                .executeUpdate();
    }

    // Разблокировка (Р-40), часть вторая: истёкшие за время блокировки уходят в EXPIRED, а не
    // в ACTIVE — иначе ссылка числится оплачиваемой и отказывает каждому плательщику, пока её
    // не догонит планировщик. Возвращает число просроченных ссылок.
    public int expireSuspendedLinks(Integer terminalId, Instant now) {
        if (linksTableMissing()) {
            return 0;
        }
        return entityManager.createNativeQuery(
                        "UPDATE payment_links SET status = :expired "
                                + "WHERE terminal_id = :terminalId AND status = :suspended "
                                + "AND expires_at IS NOT NULL AND expires_at <= :now")
                .setParameter("expired", EXPIRED)
                .setParameter("terminalId", terminalId)
                .setParameter("suspended", SUSPENDED)
                .setParameter("now", now)
                .executeUpdate();
    }

    // Таблицы payment_links нет ровно там, где pbl ни разу не мигрировал: ссылок тогда нет вовсе,
    // и блокировка обязана пройти, иначе админ получит 500 при первом развёртывании, где directory
    // поднялся первым (та же проблема порядка старта, что и P1-2). Проверка на каждый вызов:
    // кэш «отсутствует» протухнет ровно в момент выката pbl, а блокировка — редкое действие.
    private boolean linksTableMissing() {
        try (Connection connection = dataSource.getConnection()) {
            if (tableExists(connection, TABLE) || tableExists(connection, TABLE.toUpperCase(Locale.ROOT))) {
                return false;
            }
        } catch (SQLException e) {
            // Не «отсутствует», а «неизвестно»: если база правда недоступна, транзакция упадёт сама.
            log.warn("Could not determine whether {} exists; attempting the update anyway", TABLE, e);
            return false;
        }
        log.warn("Table {} is absent from this database: no payment links to move. "
                + "Expected only where pbl has never migrated against it.", TABLE);
        return true;
    }

    private static boolean tableExists(Connection connection, String name) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, name, null)) {
            return tables.next();
        }
    }
}
