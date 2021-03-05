package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.rest.UpdateCreateDeleteWithTenantJpaRepository;

public interface AlgoStrategyJpaRepository extends JpaRepository<AlgoStrategy, Integer>,
    AlgoStrategyJpaRepositoryCustom, UpdateCreateDeleteWithTenantJpaRepository<AlgoStrategy> {

  List<AlgoStrategy> findByIdAlgoAssetclassSecurityAndIdTenant(Integer idAlgoAssetclassSecurity, Integer idTenant);

  @Query(value = "SELECT dtype FROM algo_top_asset_security WHERE id_algo_assetclass_security = ?1 AND id_tenant = ?2", nativeQuery = true)
  String getAlgoLevelType(Integer idAlgoAssetclassSecurity, Integer idTenant);

  @Transactional
  @Modifying
  int deleteByIdAlgoRuleStrategyAndIdTenant(Integer idAlgoStrategy, Integer idTenant);
}
