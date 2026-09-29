package az.millikart.pbl.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Креды компании из общей таблицы companies (Р-93): строки пишет directory, pbl только читает —
// поэтому запрос, а не сущность.
@Repository
public class CompanyCredentialsRepository {

    private final JdbcTemplate jdbcTemplate;

    public CompanyCredentialsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Пароль — шифротекст CredentialCipher. Пусто у компаний, заведённых до Р-93.
    public record StoredCredentials(String login, String encryptedPassword) {

        @Override
        public String toString() {
            return "StoredCredentials[login=" + login + ", encryptedPassword=********]";
        }
    }

    public Optional<StoredCredentials> findByCompanyId(String companyId) {
        List<StoredCredentials> rows = jdbcTemplate.query(
                "SELECT provider_login, provider_password FROM companies WHERE id = ?",
                (rs, rowNum) -> new StoredCredentials(rs.getString(1), rs.getString(2)),
                companyId);
        return rows.stream().findFirst();
    }
}
