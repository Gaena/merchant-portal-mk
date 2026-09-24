package az.millikart.ecom.repository;

import az.millikart.ecom.domain.ProviderLogin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderLoginRepository extends JpaRepository<ProviderLogin, Long> {
}
