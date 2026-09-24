package az.millikart.directory.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

// Читает provider_logins — слепок логинов мультимерчантов провайдера, который держит ecom (Р-94).
// Тот же приём, что у ProviderTerminalStatusRepository: база одна, писатель слепка один — ecom, и
// второго JPA-маппинга чужой таблицы здесь быть не должно.
@Repository
public class ProviderLoginSnapshotRepository {

    private static final Logger log = LoggerFactory.getLogger(ProviderLoginSnapshotRepository.class);

    private static final String TABLE = "provider_logins";

    @PersistenceContext
    private EntityManager entityManager;

    private final DataSource dataSource;

    public ProviderLoginSnapshotRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Связь логина с мерчантом; у логина без связей linkStatus и merchantRid пусты.
    public record LoginLink(String loginStatus, String linkStatus, String merchantRid) {
    }

    // Слепок снят хотя бы раз: таблица есть и не пуста. Где ecom не развёрнут, её нет вовсе.
    public boolean synchronised() {
        if (tableMissing()) {
            return false;
        }
        Number rows = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM provider_logins")
                .getSingleResult();
        return rows.longValue() > 0;
    }

    // login — без префикса владельца, как его хранит провайдер. Сравнение точное.
    @SuppressWarnings("unchecked")
    public List<LoginLink> linksOf(String login) {
        if (tableMissing()) {
            return List.of();
        }
        List<Object[]> rows = entityManager
                .createNativeQuery("SELECT login_status, link_status, merchant_rid FROM provider_logins WHERE login = :login")
                .setParameter("login", login)
                .getResultList();
        return rows.stream()
                .map(row -> new LoginLink(
                        row[0] != null ? String.valueOf(row[0]) : null,
                        row[1] != null ? String.valueOf(row[1]) : null,
                        row[2] != null ? String.valueOf(row[2]) : null))
                .toList();
    }

    private boolean tableMissing() {
        try (Connection connection = dataSource.getConnection()) {
            if (tableExists(connection, TABLE) || tableExists(connection, TABLE.toUpperCase(Locale.ROOT))) {
                return false;
            }
        } catch (SQLException e) {
            // Не «отсутствует», а «неизвестно»: если база правда недоступна, запрос упадёт сам.
            log.warn("Could not determine whether {} exists; attempting the read anyway", TABLE, e);
            return false;
        }
        return true;
    }

    private static boolean tableExists(Connection connection, String name) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, name, null)) {
            return tables.next();
        }
    }
}
