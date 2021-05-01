package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.AlgoSecurity;

public class AlgoSecurityJpaRepositoryImpl extends BaseRepositoryImpl<AlgoSecurity>
    implements AlgoSecurityJpaRepositoryCustom {

  @Autowired
  AlgoSecurityJpaRepository algoSecurityJpaRepository;

  @Override
  public AlgoSecurity saveOnlyAttributes(AlgoSecurity algoSecurity, AlgoSecurity existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    return algoSecurityJpaRepository.save(algoSecurity);
  }

  public int delEntityWithTenant(Integer idAlgoAssetclassSecurity, Integer idTenant) {
    return algoSecurityJpaRepository.deleteByIdAlgoAssetclassSecurityAndIdTenant(idAlgoAssetclassSecurity, idTenant);
  }

}
