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

  @Override
  public AlgoAssetclass saveOnlyAttributes(AlgoAssetclass algoAssetclass, AlgoAssetclass existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    validateMutualExclusivity(algoAssetclass);
    return algoAssetclassJpaRepository.save(algoAssetclass);
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

  public int delEntityWithTenant(Integer idAlgoAssetclassSecurity, Integer idTenant) {
    return algoAssetclassJpaRepository.deleteByIdAlgoAssetclassSecurityAndIdTenant(idAlgoAssetclassSecurity, idTenant);
  }

}
