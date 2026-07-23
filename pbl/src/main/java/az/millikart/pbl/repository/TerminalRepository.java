package az.millikart.pbl.repository;

import az.millikart.pbl.domain.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalRepository extends JpaRepository<Terminal, Integer> {
    java.util.List<Terminal> findAllByCompanyId(String companyId);
}
