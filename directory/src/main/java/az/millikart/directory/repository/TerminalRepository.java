package az.millikart.directory.repository;

import az.millikart.directory.domain.Terminal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalRepository extends JpaRepository<Terminal, Integer> {
    List<Terminal> findAllByCompanyId(String companyId);
}
