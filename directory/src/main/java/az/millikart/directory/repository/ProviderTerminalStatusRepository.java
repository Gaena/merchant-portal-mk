package az.millikart.directory.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Читает `provider_terminals` — слепок терминалов провайдера, который держит модуль `ecom`.
 *
 * Тот же кросс-модульный приём, что и у `PaymentLinkStatusRepository`, и по той же причине:
 * брокера сообщений нет, а база у всех сервисов одна. Разница в направлении — сюда только
 * читают. Единственный писатель слепка — синхронизация в `ecom`, и второй JPA-маппинг чужой
 * таблицы здесь заводить нельзя: он молча разойдётся с ней.
 *
 * Таблицы может не быть вовсе: `ecom` разворачивается не везде, а справочник терминалов должен
 * работать и без него. Отсутствие таблицы означает «сверять не с чем» — сверка просто не идёт,
 * и ни один статус от этого не меняется.
 */
@Repository
public class ProviderTerminalStatusRepository {

    private static final Logger log = LoggerFactory.getLogger(ProviderTerminalStatusRepository.class);

    private static final String TABLE = "provider_terminals";

    @PersistenceContext
    private EntityManager entityManager;

    private final DataSource dataSource;

    public ProviderTerminalStatusRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean snapshotAvailable() {
        return !snapshotTableMissing();
    }

    /**
     * Активность терминалов провайдера: rid — активен ли он в последнем применённом слепке.
     *
     * Отсутствие rid в карте — это не «выключен»: строки может не быть, потому что синхронизация
     * ещё ни разу не прошла или терминал не приходил никогда. Выключение фиксируется явным
     * `active = false`, и только оно что-то меняет у нас.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Boolean> activityByRid() {
        if (snapshotTableMissing()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager
                .createNativeQuery("SELECT rid, active FROM provider_terminals")
                .getResultList();

        Map<String, Boolean> activity = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                activity.put(String.valueOf(row[0]), Boolean.TRUE.equals(row[1]));
            }
        }
        return activity;
    }

    /** Строка слепка: то, что провайдер знает о своём терминале. Пароля у него мы не спрашиваем. */
    public record ProviderTerminalRow(String rid, String title, String login, boolean active) {
    }

    @SuppressWarnings("unchecked")
    public Optional<ProviderTerminalRow> findByRid(String rid) {
        if (snapshotTableMissing()) {
            return Optional.empty();
        }
        List<Object[]> rows = entityManager
                .createNativeQuery("SELECT rid, title, login, active FROM provider_terminals WHERE rid = :rid")
                .setParameter("rid", rid)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = rows.getFirst();
        return Optional.of(new ProviderTerminalRow(
                String.valueOf(row[0]),
                row[1] != null ? String.valueOf(row[1]) : null,
                row[2] != null ? String.valueOf(row[2]) : null,
                Boolean.TRUE.equals(row[3])));
    }

    private boolean snapshotTableMissing() {
        try (Connection connection = dataSource.getConnection()) {
            if (tableExists(connection, TABLE) || tableExists(connection, TABLE.toUpperCase(Locale.ROOT))) {
                return false;
            }
        } catch (SQLException e) {
            // Не «отсутствует», а «неизвестно»: если база правда недоступна, запрос упадёт сам.
            log.warn("Could not determine whether {} exists; attempting the read anyway", TABLE, e);
            return false;
        }
        log.info("Table {} is absent from this database: nothing to reconcile terminal statuses "
                + "against. Expected wherever ecom is not deployed.", TABLE);
        return true;
    }

    private static boolean tableExists(Connection connection, String name) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, name, null)) {
            return tables.next();
        }
    }
}
