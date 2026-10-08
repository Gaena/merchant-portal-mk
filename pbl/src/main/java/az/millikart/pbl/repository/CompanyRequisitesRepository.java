package az.millikart.pbl.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Название и VÖEN компании для чека плательщика (Р-130). Строки пишет directory, pbl только читает —
// поэтому запрос, а не сущность. VÖEN пуст у компаний, заведённых без него (Р-129).
@Repository
public class CompanyRequisitesRepository {

    private final JdbcTemplate jdbcTemplate;

    public CompanyRequisitesRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CompanyRequisites(String name, String taxId) {
    }

    public Optional<CompanyRequisites> findByCompanyId(String companyId) {
        if (companyId == null) {
            return Optional.empty();
        }
        List<CompanyRequisites> rows = jdbcTemplate.query(
                "SELECT name, tax_id FROM companies WHERE id = ?",
                (rs, rowNum) -> new CompanyRequisites(rs.getString(1), rs.getString(2)),
                companyId);
        return rows.stream().findFirst();
    }
}
