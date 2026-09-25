package az.millikart.ecom.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;

// Логины мультимерчанта компаний (Р-93) — скоуп выписки (Р-97). Таблица чужая (auth и directory),
// поэтому нативный запрос, а не сущность: сущность проверялась бы на старте, а порядок старта сервисов
// значения не имеет (P1-2). Пользователь с токеном значит, что auth стартовал и companies уже есть.
@Repository
public class CompanyLoginRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<String> providerLoginOf(String companyId) {
        return logins(entityManager
                .createNativeQuery("SELECT provider_login FROM companies WHERE id = :id AND provider_login IS NOT NULL")
                .setParameter("id", companyId)
                .getResultList());
    }

    // Все компании, и удалённые тоже: у их мерчантов остаётся история.
    public List<String> allProviderLogins() {
        return logins(entityManager
                .createNativeQuery("SELECT provider_login FROM companies WHERE provider_login IS NOT NULL")
                .getResultList());
    }

    private static List<String> logins(List<?> rows) {
        return rows.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }
}
