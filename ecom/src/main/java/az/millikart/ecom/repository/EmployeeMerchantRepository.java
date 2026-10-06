package az.millikart.ecom.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

// Мерчанты терминалов, назначенных сотруднику (Р-131). user_terminals и terminals — чужие таблицы (auth,
// directory): нативный запрос, а не сущность. ecom стартует после directory (AGENTS §4) — таблицы на месте.
@Repository
public class EmployeeMerchantRepository {

    @PersistenceContext
    private EntityManager entityManager;

    // Только терминалы его компании: перенесённый из скоупа выпадает сам. Терминал без merchant_rid выписке
    // ничего не даёт. userId не UUID — назначений нет: пусто, а не «вся компания».
    public List<String> assignedMerchantRids(String userId, String companyId) {
        UUID id = parse(userId);
        if (id == null || companyId == null) {
            return List.of();
        }
        List<?> rows = entityManager.createNativeQuery(
                        "SELECT t.merchant_rid FROM user_terminals ut JOIN terminals t ON t.id = ut.terminal_id "
                                + "WHERE ut.user_id = :userId AND t.company_id = :companyId AND t.merchant_rid IS NOT NULL")
                .setParameter("userId", id)
                .setParameter("companyId", companyId)
                .getResultList();
        return rows.stream().filter(Objects::nonNull).map(String::valueOf).distinct().toList();
    }

    private static UUID parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
