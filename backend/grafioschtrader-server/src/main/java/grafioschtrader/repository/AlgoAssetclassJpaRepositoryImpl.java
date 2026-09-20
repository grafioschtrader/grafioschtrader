package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafioschtrader.entities.AlgoAssetclass;

public class AlgoAssetclassJpaRepositoryImpl extends BaseRepositoryImpl<AlgoAssetclass>
    implements AlgoAssetclassJpaRepositoryCustom {

  @Autowired
  private AlgoAssetclassJpaRepository algoAssetclassJpaRepository;

  @Autowired
  private grafioschtrader.service.AlgoAlertScopeLifecycle alertScopeLifecycle;

  @Autowired
  private grafioschtrader.service.AlgoHierarchyWriteGuard hierarchyWriteGuard;

  @Override
  public AlgoAssetclass saveOnlyAttributes(AlgoAssetclass algoAssetclass, AlgoAssetclass existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    hierarchyWriteGuard.assertHierarchyWritable();
    validateRebalancingOverrides(algoAssetclass);
    validateMutualExclusivity(algoAssetclass);
    var before = alertScopeLifecycle.snapshot(algoAssetclass.getIdTenant());
    AlgoAssetclass saved = algoAssetclassJpaRepository.save(algoAssetclass);
    alertScopeLifecycle.changed(saved.getIdTenant(), before);
    return saved;
  }

  private void validateMutualExclusivity(AlgoAssetclass algoAssetclass) {
    boolean hasName = algoAssetclass.getName() != null;
    boolean hasAssetclass = algoAssetclass.getAssetclass() != null || algoAssetclass.getCategoryType() != null
        || algoAssetclass.getSpecialInvestmentInstrument() != null;
    if (hasName && hasAssetclass) {
      throw new DataViolationException("name", "algo.assetclass.name.or.assetclass", null);
    }
    if (!hasName && !hasAssetclass) {
      throw new DataViolationException("name", "algo.assetclass.name.or.assetclass.required", null);
    }
  }

  private void validateRebalancingOverrides(AlgoAssetclass entity) {
    Double deviation = entity.getSecurityDeviationPercentage();
    if (deviation != null && (!Double.isFinite(deviation) || deviation < 0 || deviation > 100)) {
      throw new DataViolationException("security.deviation.percentage", "algo.rebalancing.invalid.band", null);
    }
    if (entity.getMaxTradedSecuritiesPerAssetclass() != null && entity.getMaxTradedSecuritiesPerAssetclass() < 1) {
      throw new DataViolationException("max.traded.securities.per.assetclass", "algo.rebalancing.invalid.limit", null);
    }
  }

  @Autowired
  private AlgoTradingRepository tradingRepository;

  public int delEntityWithTenant(Integer idAlgoAssetclassSecurity, Integer idTenant) {
    hierarchyWriteGuard.assertHierarchyWritable();
    int deleted = algoAssetclassJpaRepository.deleteByIdAlgoAssetclassSecurityAndIdTenant(idAlgoAssetclassSecurity,
        idTenant);
    tradingRepository.clearRemovedAssignments(idTenant);
    return deleted;
  }

}
