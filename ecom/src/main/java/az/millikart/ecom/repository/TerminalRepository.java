package az.millikart.ecom.repository;

import az.millikart.ecom.domain.Terminal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerminalRepository extends JpaRepository<Terminal, Integer> {

    List<Terminal> findByCompanyId(String companyId);
}
