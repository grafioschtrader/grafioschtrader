package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementations;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.User;

public class AlgoStrategyJpaRepositoryImpl extends BaseRepositoryImpl<AlgoStrategy>
    implements AlgoStrategyJpaRepositoryCustom {

  @Autowired
  AlgoStrategyJpaRepository algoStrategyJpaRepository;

  @Override
  public AlgoStrategy saveOnlyAttributes(AlgoStrategy algoStrategy, AlgoStrategy existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    return algoStrategyJpaRepository.save(algoStrategy);
  }

  public int delEntityWithTenant(Integer idAlgoStrategy, Integer idTenant) {
    return algoStrategyJpaRepository.deleteByIdAlgoRuleStrategyAndIdTenant(idAlgoStrategy, idTenant);
  }

  @Override
  public Set<AlgoStrategyImplementations> getUnusedStrategiesForManualAdding(Integer idAlgoAssetclassSecurity) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    String algoLevelType = algoStrategyJpaRepository.getAlgoLevelType(idAlgoAssetclassSecurity, user.getIdTenant());
    if (algoLevelType != null) {
      List<AlgoStrategy> existingAlgoStrategies = algoStrategyJpaRepository
          .findByIdAlgoAssetclassSecurityAndIdTenant(idAlgoAssetclassSecurity, user.getIdTenant());
      Set<AlgoStrategyImplementations> existingSet = existingAlgoStrategies.stream()
          .map(strategy -> strategy.getAlgoStrategyImplementations()).collect(Collectors.toSet());
      return StrategyHelper.getUnusedStrategiesForManualAdding(existingSet, algoLevelType);
    }
    return null;
  }

}
