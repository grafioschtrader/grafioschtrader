package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.rest.UpdateCreateDeleteWithTenantJpaRepository;

@Repository
public interface AlgoSecurityJpaRepository extends JpaRepository<AlgoSecurity, Integer>,
    AlgoSecurityJpaRepositoryCustom, UpdateCreateDeleteWithTenantJpaRepository<AlgoSecurity> {

  @Transactional
  @Modifying
  int deleteByIdAlgoAssetclassSecurityAndIdTenant(Integer idAlgoAssetclassSecurity, Integer idTenant);

}
