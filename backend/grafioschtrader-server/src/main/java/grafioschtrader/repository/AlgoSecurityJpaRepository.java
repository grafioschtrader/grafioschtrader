package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafioschtrader.entities.AlgoSecurity;

public interface AlgoSecurityJpaRepository extends JpaRepository<AlgoSecurity, Integer>,
    AlgoSecurityJpaRepositoryCustom, UpdateCreateDeleteWithTenantJpaRepository<AlgoSecurity> {

  @Transactional
  @Modifying
  int deleteByIdAlgoAssetclassSecurityAndIdTenant(Integer idAlgoAssetclassSecurity, Integer idTenant);

  /** All AlgoSecurity children of a given AlgoAssetclass parent within a tenant. */
  List<AlgoSecurity> findByIdAlgoSecurityParentAndIdTenant(Integer idAlgoSecurityParent, Integer idTenant);

  /** All AlgoSecurity entries for a given tenant (used by tenant alert overview). */
  List<AlgoSecurity> findByIdTenant(Integer idTenant);

  /**
   * All standalone alert nodes, the ones a user added straight from a watchlist or portfolio row rather than inside an
   * AlgoTop hierarchy. Deactivated nodes are included on purpose: the evaluation has to see them in order to discard
   * their crossing baselines, so that switching an alert on again cannot report a price move that happened while it was
   * off.
   */
  List<AlgoSecurity> findByIdAlgoSecurityParentIsNull();

  /**
   * The standalone alert node of one instrument within a tenant, if there is one. Restricted to nodes without a parent,
   * so that an instrument which also sits inside an AlgoTop hierarchy is not mistaken for a standalone alert.
   */
  AlgoSecurity findBySecurity_idSecuritycurrencyAndIdTenantAndIdAlgoSecurityParentIsNull(Integer idSecuritycurrency,
      Integer idTenant);

}
