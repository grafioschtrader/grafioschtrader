package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafioschtrader.entities.AlgoSecurity;

public interface AlgoSecurityJpaRepository extends JpaRepository<AlgoSecurity, Integer>,
    AlgoSecurityJpaRepositoryCustom, UpdateCreateDeleteWithTenantJpaRepository<AlgoSecurity> {

  AlgoSecurity findBySecurity_idSecuritycurrencyAndIdTenant(Integer idSecuritycurrency, Integer idTenant);
  
  @Transactional
  @Modifying
  int deleteByIdAlgoAssetclassSecurityAndIdTenant(Integer idAlgoAssetclassSecurity, Integer idTenant);

}
