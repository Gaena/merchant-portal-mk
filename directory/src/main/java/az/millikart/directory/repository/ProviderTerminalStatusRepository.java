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

// Слепок provider_terminals пишет только ecom, здесь его только читают. Своего JPA-маппинга чужой
// таблицы не заводить: он молча разойдётся с ней. Таблицы ещё нет (ecom не стартовал) — сверять не
// с чем, и ни один статус не меняется.
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

    // Нет rid в карте — «не знаем», а не «выключен»: у нас что-то меняет только явный active = false.
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

    public record ProviderTerminalRow(String rid, String title, String login, boolean active, String terminalRid) {

        static final String TERMINAL_OWNER_PREFIX = "TerminalSys/";

        // В terminals логин лежит с префиксом владельца («TerminalSys/Admin»), слепок — без него (Р-83).
        public String gatewayLogin() {
            if (login == null || login.isBlank()) {
                return null;
            }
            String value = login.trim();
            return value.startsWith(TERMINAL_OWNER_PREFIX) ? value : TERMINAL_OWNER_PREFIX + value;
        }
    }

    @SuppressWarnings("unchecked")
    public Optional<ProviderTerminalRow> findByRid(String rid) {
        if (snapshotTableMissing()) {
            return Optional.empty();
        }
        List<Object[]> rows = entityManager
                .createNativeQuery("SELECT rid, title, login, active, terminal_rid FROM provider_terminals WHERE rid = :rid")
                .setParameter("rid", rid)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toRow(rows.getFirst()));
    }

    // Сверке — переносить в terminals смену названия, логина и номера у провайдера (Р-67, Р-96).
    @SuppressWarnings("unchecked")
    public Map<String, ProviderTerminalRow> rowsByRid() {
        if (snapshotTableMissing()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager
                .createNativeQuery("SELECT rid, title, login, active, terminal_rid FROM provider_terminals")
                .getResultList();
        Map<String, ProviderTerminalRow> byRid = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                byRid.put(String.valueOf(row[0]), toRow(row));
            }
        }
        return byRid;
    }

    private static ProviderTerminalRow toRow(Object[] row) {
        return new ProviderTerminalRow(
                String.valueOf(row[0]),
                row[1] != null ? String.valueOf(row[1]) : null,
                row[2] != null ? String.valueOf(row[2]) : null,
                Boolean.TRUE.equals(row[3]),
                row[4] != null ? String.valueOf(row[4]) : null);
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
                + "against. Expected until ecom has started once.", TABLE);
        return true;
    }

    private static boolean tableExists(Connection connection, String name) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, name, null)) {
            return tables.next();
        }
    }
}
