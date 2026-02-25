package grafioschtrader.repository;

import java.util.Collection;
import java.util.List;

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

  /** All AlgoSecurity children of a given AlgoAssetclass parent within a tenant. */
  List<AlgoSecurity> findByIdAlgoSecurityParentAndIdTenant(Integer idAlgoSecurityParent, Integer idTenant);

  /** All AlgoSecurity entries for a given tenant (used by tenant alert overview). */
  List<AlgoSecurity> findByIdTenant(Integer idTenant);

  /** Tier 2: all active standalone alerts (no parent AlgoAssetclass). */
  List<AlgoSecurity> findByActivatableTrueAndIdAlgoSecurityParentIsNull();

  /** Tier 1: active standalone alerts matching specific updated securities. */
  List<AlgoSecurity> findByActivatableTrueAndIdAlgoSecurityParentIsNullAndSecurity_idSecuritycurrencyIn(
      Collection<Integer> securityIds);

}
