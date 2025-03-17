package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.entities.User;
import grafiosch.repository.BaseRepositoryImpl;
import grafioschtrader.algo.strategy.model.AlgoLevelType;
import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.entities.AlgoStrategy;

public class AlgoStrategyJpaRepositoryImpl extends BaseRepositoryImpl<AlgoStrategy>
    implements AlgoStrategyJpaRepositoryCustom {

  @Autowired
  private AlgoStrategyJpaRepository algoStrategyJpaRepository;

  @Override
  public AlgoStrategy saveOnlyAttributes(AlgoStrategy algoStrategy, AlgoStrategy existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    return algoStrategyJpaRepository.save(algoStrategy);
  }

  public int delEntityWithTenant(Integer idAlgoStrategy, Integer idTenant) {
    return algoStrategyJpaRepository.deleteByIdAlgoRuleStrategyAndIdTenant(idAlgoStrategy, idTenant);
  }

  @Override
  public Set<AlgoStrategyImplementationType> getStrategiesForLevel(AlgoLevelType algoLevelType) {
    return StrategyHelper.getUnusedStrategiesForManualAdding(Collections.<AlgoStrategyImplementationType>emptySet(),
        algoLevelType);
  }

  @Override
  public Set<AlgoStrategyImplementationType> getUnusedStrategiesForManualAdding(Integer idAlgoAssetclassSecurity) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    String algoLevel = algoStrategyJpaRepository.getAlgoLevelType(idAlgoAssetclassSecurity, user.getIdTenant());
    if (algoLevel != null) {
      List<AlgoStrategy> existingAlgoStrategies = algoStrategyJpaRepository
          .findByIdAlgoAssetclassSecurityAndIdTenant(idAlgoAssetclassSecurity, user.getIdTenant());
      Set<AlgoStrategyImplementationType> existingSet = existingAlgoStrategies.stream()
          .map(strategy -> strategy.getAlgoStrategyImplementations()).collect(Collectors.toSet());
      return StrategyHelper.getUnusedStrategiesForManualAdding(existingSet, AlgoLevelType.getAlgoLeveType(algoLevel));
    }
    return null;
  }

}
