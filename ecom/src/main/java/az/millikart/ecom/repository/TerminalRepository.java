package az.millikart.ecom.repository;

import az.millikart.ecom.domain.Terminal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TerminalRepository extends JpaRepository<Terminal, Integer> {

    @Query("select t.merchantRid from Terminal t "
            + "where t.companyId = :companyId and t.merchantRid is not null")
    List<String> findMerchantRidsByCompany(String companyId);

    @Query("select t.merchantRid from Terminal t where t.merchantRid is not null")
    List<String> findAllMerchantRids();
}
