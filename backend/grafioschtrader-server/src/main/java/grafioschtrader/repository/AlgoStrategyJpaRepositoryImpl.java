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
import grafioschtrader.algo.strategy.model.StrategyClassBindingDefinition;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.algo.strategy.model.complex.StrategyConfigValidator;
import grafioschtrader.entities.AlgoStrategy;
import tools.jackson.databind.ObjectMapper;

public class AlgoStrategyJpaRepositoryImpl extends BaseRepositoryImpl<AlgoStrategy>
    implements AlgoStrategyJpaRepositoryCustom {

  @Autowired
  private AlgoStrategyJpaRepository algoStrategyJpaRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public AlgoStrategy saveOnlyAttributes(AlgoStrategy algoStrategy, AlgoStrategy existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    if (algoStrategy.getStrategyConfig() != null) {
      StrategyClassBindingDefinition scbd = StrategyHelper.getStrategyBindingMap()
          .get(algoStrategy.getAlgoStrategyImplementations());
      if (scbd != null && scbd.complexConfigClass != null) {
        try {
          StrategyConfigValidator.parseAndValidate(algoStrategy.getStrategyConfig(), objectMapper);
        } catch (Exception e) {
          throw new IllegalArgumentException(e.getMessage(), e);
        }
      }
    }
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
    String algoLevel = algoStrategyJpaRepository.getAlgoLevelType(idAlgoAssetclassSecurity, user.getActualIdTenant());
    if (algoLevel != null) {
      List<AlgoStrategy> existingAlgoStrategies = algoStrategyJpaRepository
          .findByIdAlgoAssetclassSecurityAndIdTenant(idAlgoAssetclassSecurity, user.getActualIdTenant());
      Set<AlgoStrategyImplementationType> existingSet = existingAlgoStrategies.stream()
          .map(strategy -> strategy.getAlgoStrategyImplementations()).collect(Collectors.toSet());
      return StrategyHelper.getUnusedStrategiesForManualAdding(existingSet, AlgoLevelType.getAlgoLeveType(algoLevel));
    }
    return null;
  }

}
