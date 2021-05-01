package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.AlgoAssetclass;

public class AlgoAssetclassJpaRepositoryImpl extends BaseRepositoryImpl<AlgoAssetclass>
    implements AlgoAssetclassJpaRepositoryCustom {

  @Autowired
  AlgoAssetclassJpaRepository algoAssetclassJpaRepository;

  @Override
  public AlgoAssetclass saveOnlyAttributes(AlgoAssetclass algoAssetclass, AlgoAssetclass existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    return algoAssetclassJpaRepository.save(algoAssetclass);
  }

  public int delEntityWithTenant(Integer idAlgoAssetclassSecurity, Integer idTenant) {
    return algoAssetclassJpaRepository.deleteByIdAlgoAssetclassSecurityAndIdTenant(idAlgoAssetclassSecurity, idTenant);
  }

}
