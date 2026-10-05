package az.millikart.directory.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

// Слепок provider_logins пишет только ecom, здесь его только читают (Р-94). Своего JPA-маппинга
// чужой таблицы не заводить: он молча разойдётся с ней.
@Repository
public class ProviderLoginSnapshotRepository {

    private static final String TABLE = "provider_logins";
    private static final String ACTIVE = "Active";

    @PersistenceContext
    private EntityManager entityManager;

    private final SharedTables sharedTables;

    public ProviderLoginSnapshotRepository(SharedTables sharedTables) {
        this.sharedTables = sharedTables;
    }

    // Связь логина с мерчантом; у логина без связей linkStatus и merchantRid пусты.
    public record LoginLink(String loginStatus, String linkStatus, String merchantRid) {
    }

    public boolean synchronised() {
        if (tableMissing()) {
            return false;
        }
        Number rows = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM provider_logins")
                .getSingleResult();
        return rows.longValue() > 0;
    }

    // Логин, который пройдёт проверку компании; merchants — названия мерчантов для формы (Р-95).
    public record EligibleLogin(String login, List<String> merchants) {
    }

    @SuppressWarnings("unchecked")
    public List<EligibleLogin> eligibleLogins() {
        if (tableMissing()) {
            return List.of();
        }
        List<Object[]> rows = entityManager
                .createNativeQuery("SELECT login, merchant_title FROM provider_logins "
                        + "WHERE login_status = 'Active' AND link_status = 'Active' AND merchant_rid IS NOT NULL "
                        + "ORDER BY login, merchant_title")
                .getResultList();
        Map<String, List<String>> byLogin = new LinkedHashMap<>();
        for (Object[] row : rows) {
            List<String> merchants = byLogin.computeIfAbsent(String.valueOf(row[0]), login -> new ArrayList<>());
            if (row[1] != null) {
                merchants.add(String.valueOf(row[1]));
            }
        }
        return byLogin.entrySet().stream()
                .map(entry -> new EligibleLogin(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
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

    // Мерчанты, за которых логин вправе ходить к провайдеру: связь и сам логин Active (Р-96). Одно правило
    // на заведение и перенос терминала и на смену логина компании — разойдутся, и проверки разъедутся.
    public Set<String> activeMerchantRidsOf(String login) {
        return linksOf(login).stream()
                .filter(link -> ACTIVE.equals(link.loginStatus()) && ACTIVE.equals(link.linkStatus())
                        && link.merchantRid() != null)
                .map(LoginLink::merchantRid)
                .collect(Collectors.toSet());
    }

    private boolean tableMissing() {
        return sharedTables.missing(TABLE);
    }
}
