package az.millikart.directory.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

// Пишет в payment_links модуля pbl — осознанный долг (Р-39, AGENTS.md §10). Не entity: второй
// JPA-маппинг чужой таблицы молча разойдётся с ней. Каждый UPDATE поднимает version (@Version в pbl): иначе
// pbl, прочитавший ссылку раньше, сохранил бы её целиком и вернул прежний статус.
@Repository
public class PaymentLinkStatusRepository {

    private static final Logger log = LoggerFactory.getLogger(PaymentLinkStatusRepository.class);

    // Литералы: enum модуля pbl не на classpath этого модуля.
    private static final String ACTIVE = "ACTIVE";
    private static final String SUSPENDED = "SUSPENDED";
    private static final String EXPIRED = "EXPIRED";

    private static final String TABLE = "payment_links";

    // NULL у строк, вставленных мимо Hibernate: NULL + 1 остался бы NULL.
    private static final String BUMP_VERSION = "version = COALESCE(version, 0) + 1 ";

    @PersistenceContext
    private EntityManager entityManager;

    private final SharedTables sharedTables;

    public PaymentLinkStatusRepository(SharedTables sharedTables) {
        this.sharedTables = sharedTables;
    }

    // Только ACTIVE: остальные статусы — факты о прошлом ссылки, иначе разблокировка не узнает,
    // чем она была (Р-39).
    public int suspendActiveLinks(Integer terminalId) {
        if (linksTableMissing()) {
            return 0;
        }
        return entityManager.createNativeQuery(
                        "UPDATE payment_links SET status = :suspended, " + BUMP_VERSION
                                + "WHERE terminal_id = :terminalId AND status = :active")
                .setParameter("suspended", SUSPENDED)
                .setParameter("terminalId", terminalId)
                .setParameter("active", ACTIVE)
                .executeUpdate();
    }

    // NULL в expires_at — ссылка без срока (старше P1-9): платёжный путь считает её живой (Р-40).
    public int resumeSuspendedLinks(Integer terminalId, Instant now) {
        if (linksTableMissing()) {
            return 0;
        }
        return entityManager.createNativeQuery(
                        "UPDATE payment_links SET status = :active, " + BUMP_VERSION
                                + "WHERE terminal_id = :terminalId AND status = :suspended "
                                + "AND (expires_at IS NULL OR expires_at > :now)")
                .setParameter("active", ACTIVE)
                .setParameter("terminalId", terminalId)
                .setParameter("suspended", SUSPENDED)
                .setParameter("now", now)
                .executeUpdate();
    }

    // Не в ACTIVE: такая ссылка отказывала бы каждому плательщику, пока её не догонит
    // планировщик (Р-40).
    public int expireSuspendedLinks(Integer terminalId, Instant now) {
        if (linksTableMissing()) {
            return 0;
        }
        return entityManager.createNativeQuery(
                        "UPDATE payment_links SET status = :expired, " + BUMP_VERSION
                                + "WHERE terminal_id = :terminalId AND status = :suspended "
                                + "AND expires_at IS NOT NULL AND expires_at <= :now")
                .setParameter("expired", EXPIRED)
                .setParameter("terminalId", terminalId)
                .setParameter("suspended", SUSPENDED)
                .setParameter("now", now)
                .executeUpdate();
    }

    // Таблицы нет, пока pbl ни разу не мигрировал: ссылок нет, и блокировка обязана пройти, а не
    // дать 500 (P1-2). Проверка на каждый вызов: кэш «нет таблицы» протух бы при выкате pbl.
    private boolean linksTableMissing() {
        if (!sharedTables.missing(TABLE)) {
            return false;
        }
        log.warn("Table {} is absent from this database: no payment links to move. "
                + "Expected only where pbl has never migrated against it.", TABLE);
        return true;
    }
}
