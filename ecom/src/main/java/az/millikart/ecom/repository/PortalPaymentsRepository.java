package az.millikart.ecom.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

// Портальное по заказу выписки (Р-124): терминал его мерчанта, креды компании терминала и операция портала,
// если заказ завёл портал. Чужие таблицы (terminals, companies — directory и auth, transactions — pbl) —
// нативными запросами, не сущностями, как CompanyLoginRepository.
@Repository
public class PortalPaymentsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final SharedTables sharedTables;

    public PortalPaymentsRepository(SharedTables sharedTables) {
        this.sharedTables = sharedTables;
    }

    // companyId — null у терминала без компании. Один терминал провайдера — одна компания (Р-96): merchant_rid уникален.
    public record PortalTerminal(Integer terminalId, String companyId) {
    }

    public Optional<PortalTerminal> terminalOfMerchant(String merchantRid) {
        if (merchantRid == null) {
            return Optional.empty();
        }
        List<?> rows = entityManager
                .createNativeQuery("SELECT id, company_id FROM terminals WHERE merchant_rid = :rid")
                .setParameter("rid", merchantRid)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = (Object[]) rows.get(0);
        return Optional.of(new PortalTerminal(((Number) row[0]).intValue(), row[1] != null ? String.valueOf(row[1]) : null));
    }

    // Без расшифровки: только есть ли логин и пароль компании к провайдеру (Р-93).
    public boolean hasProviderCredentials(String companyId) {
        List<?> rows = entityManager
                .createNativeQuery("SELECT provider_login, provider_password FROM companies WHERE id = :id")
                .setParameter("id", companyId)
                .getResultList();
        if (rows.isEmpty()) {
            return false;
        }
        Object[] row = (Object[]) rows.get(0);
        return notBlank(row[0]) && notBlank(row[1]);
    }

    // Заказ, заведённый порталом, — операция pbl с этим номером заказа. Таблицы нет, пока pbl ни разу не
    // мигрировал на эту базу: тогда портальных заказов нет.
    public Optional<UUID> portalTransactionOf(String providerOrderId) {
        if (providerOrderId == null || sharedTables.missing("transactions")) {
            return Optional.empty();
        }
        List<?> rows = entityManager
                .createNativeQuery("SELECT id FROM transactions WHERE provider_order_id = :id")
                .setParameter("id", providerOrderId)
                .getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(String.valueOf(rows.get(0))));
    }

    private static boolean notBlank(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }
}
