package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertyOnlyCreation;
import grafiosch.entities.User;
import grafiosch.repository.BaseRepositoryImpl;
import grafioschtrader.algo.AlgoSecurityStrategyImplType;
import grafioschtrader.algo.strategy.model.AlgoLevelType;
import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.entities.AlgoSecurity;

public class AlgoSecurityJpaRepositoryImpl extends BaseRepositoryImpl<AlgoSecurity>
    implements AlgoSecurityJpaRepositoryCustom {

  @Autowired
  private AlgoSecurityJpaRepository algoSecurityJpaRepository;

  @Autowired
  private AlgoStrategyJpaRepository algoStrategyJpaRepository;
  
  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Override
  public AlgoSecurity saveOnlyAttributes(AlgoSecurity algoSecurity, AlgoSecurity existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    return algoSecurityJpaRepository.save(algoSecurity);
  }

  public int delEntityWithTenant(Integer idAlgoAssetclassSecurity, Integer idTenant) {
    return algoSecurityJpaRepository.deleteByIdAlgoAssetclassSecurityAndIdTenant(idAlgoAssetclassSecurity, idTenant);
  }

  @Override
  public AlgoSecurityStrategyImplType getAlgoSecurityStrategyImplTypeByIdSecuritycurrency(Integer idSecuritycurrency) {
    AlgoSecurityStrategyImplType assit = new AlgoSecurityStrategyImplType(
        StrategyHelper.getUnusedStrategiesForManualAdding(Collections.<AlgoStrategyImplementationType>emptySet(),
            AlgoLevelType.SECURITY_LEVEL));
    if (idSecuritycurrency != null) {
      final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
      AlgoSecurity algoSecurity = algoSecurityJpaRepository
          .findBySecurity_idSecuritycurrencyAndIdTenant(idSecuritycurrency, user.getIdTenant());
      if (algoSecurity == null) {
        algoSecurity = new AlgoSecurity();
        algoSecurity.setSecurity(securityJpaRepository.findByIdSecuritycurrency(idSecuritycurrency));
        algoSecurity.setIdTenant(user.getIdTenant());
        assit.algoSecurity = saveOnlyAttributes(algoSecurity, null,
            Set.of(PropertyAlwaysUpdatable.class, PropertyOnlyCreation.class));
        assit.wasCreated = true;
      } else {
        assit.possibleStrategyImplSet = algoStrategyJpaRepository
            .getUnusedStrategiesForManualAdding(assit.algoSecurity.getIdAlgoAssetclassSecurity());
      }
      assit.algoSecurity = algoSecurity;
    }
    return assit;
  }

}
