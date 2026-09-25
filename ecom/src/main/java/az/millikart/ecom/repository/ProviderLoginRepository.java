package az.millikart.ecom.repository;

import az.millikart.ecom.domain.ProviderLogin;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderLoginRepository extends JpaRepository<ProviderLogin, Long> {

    // Мерчанты логинов — скоуп выписки (Р-97). Только активные связи: отвязанный или перенесённый к
    // другому логину мерчант уходит из выписки прежней компании вместе с историей. Статус самого логина
    // не смотрим: заблокированный логин не делает мерчантов чужими.
    @Query("select distinct p.merchantRid from ProviderLogin p where p.login in :logins "
            + "and p.linkStatus = 'Active' and p.merchantRid is not null order by p.merchantRid")
    List<String> findLinkedMerchantRids(@Param("logins") Collection<String> logins);

    List<ProviderLogin> findByMerchantRidIn(Collection<String> merchantRids);
}
