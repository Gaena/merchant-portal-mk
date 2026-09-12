package az.millikart.ecom.repository;

import az.millikart.ecom.domain.ProviderTerminal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderTerminalRepository extends JpaRepository<ProviderTerminal, String> {

    List<ProviderTerminal> findByActiveTrueOrderByTitleAsc();
}
