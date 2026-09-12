package az.millikart.ecom.repository;

import az.millikart.ecom.domain.Terminal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TerminalRepository extends JpaRepository<Terminal, Integer> {

    @Query("select t.providerRid from Terminal t "
            + "where t.companyId = :companyId and t.providerRid is not null")
    List<String> findProviderRidsByCompany(String companyId);

    @Query("select t.providerRid from Terminal t where t.providerRid is not null")
    List<String> findAllProviderRids();
}
