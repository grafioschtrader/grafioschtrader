package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafioschtrader.algo.strategy.model.AlgoLevelType;
import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.algo.strategy.model.StrategyClassBindingDefinition;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.algo.strategy.model.alerts.AlertConfigAdapter;
import grafioschtrader.algo.strategy.model.complex.StrategyConfigValidator;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.entities.AlgoStrategy;
import tools.jackson.databind.ObjectMapper;

public class AlgoStrategyJpaRepositoryImpl extends BaseRepositoryImpl<AlgoStrategy>
    implements AlgoStrategyJpaRepositoryCustom {

  @Autowired
  private AlgoStrategyJpaRepository algoStrategyJpaRepository;

  @Autowired
  private AlgoAlertStateJpaRepository algoAlertStateJpaRepository;

  @Autowired
  private AlgoAlertEvaluationStateJpaRepository evaluationStateRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private grafioschtrader.service.AlgoHierarchyWriteGuard hierarchyWriteGuard;

  /**
   * Validates the configuration the user just entered, through the same code the evaluation will read it with, and then
   * saves it.
   *
   * <p>
   * Only complex strategies used to be validated here, so an alert saved from the generated form was stored whatever it
   * contained and a value the evaluation could not use surfaced much later, as an alert that quietly never fired.
   * Reading it back through {@link AlertConfigAdapter} on save means the configuration that is accepted is exactly the
   * configuration that can be evaluated.
   * </p>
   */
  @Override
  public AlgoStrategy saveOnlyAttributes(AlgoStrategy algoStrategy, AlgoStrategy existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    hierarchyWriteGuard.assertHierarchyWritable(algoStrategy.getIdAlgoAssetclassSecurity());
    if (existingEntity == null) {
      hierarchyWriteGuard.assertCreateWithinLimit(LimitKeyConfig.KEY_ALGO_STRATEGY, 1);
    }
    // Generic edits and older clients must not overwrite a preference owned by the monitoring overview.
    algoStrategy.setAlertEnabled(existingEntity == null || existingEntity.isAlertEnabled());
    if (algoStrategyJpaRepository.getAlgoLevelType(algoStrategy.getIdAlgoAssetclassSecurity(),
        algoStrategy.getIdTenant()) == null)
      throw new SecurityException("Strategy parent does not belong to this tenant");
    StrategyClassBindingDefinition scbd = StrategyHelper.getStrategyBindingMap()
        .get(algoStrategy.getAlgoStrategyImplementations());
    if (algoStrategy.getAlgoStrategyImplementations() == AlgoStrategyImplementationType.AS_HOLDING_TOP_REBALANCING) {
      try {
        var config = AlertConfigAdapter.read(algoStrategy,
            grafioschtrader.algo.strategy.model.rebalacing.RebalancingTop.class);
        if (config == null || !Double.isFinite(config.getSecurityDeviationPercentage()))
          throw new IllegalArgumentException("Rebalancing configuration is required and must be finite");
      } catch (RuntimeException e) {
        throw new DataViolationException("strategy.config", "algo.strategy.config.invalid",
            new Object[] { e.getMessage() });
      }
    } else if (scbd != null && scbd.complexConfigClass != null) {
      if (algoStrategy.getStrategyConfig() != null || algoStrategy.isActivatable()) {
        try {
          if (algoStrategy.isActivatable())
            StrategyConfigValidator.executable(algoStrategy.getStrategyConfig());
          else
            StrategyConfigValidator.parseAndValidate(algoStrategy.getStrategyConfig(), objectMapper);
        } catch (Exception e) {
          throw new DataViolationException("strategy.config", "algo.strategy.config.invalid",
              new Object[] { e.getMessage() });
        }
      }
    } else if (scbd != null && scbd.algoSecurityModel != null) {
      try {
        Object config = AlertConfigAdapter.read(algoStrategy, scbd.algoSecurityModel);
        if (config == null)
          throw new IllegalArgumentException("Alert configuration is required");
        if (config instanceof grafioschtrader.algo.strategy.model.alerts.ExpressionAlert expression)
          grafioschtrader.evalex.AlertExpressionSupport.validate(expression.getExpression());
      } catch (RuntimeException e) {
        throw new DataViolationException("strategy.config", "algo.strategy.config.invalid",
            new Object[] { e.getMessage() });
      }
    }

    AlgoStrategy saved = algoStrategyJpaRepository.save(algoStrategy);
    if (existingEntity != null) {
      // The thresholds may have moved. A baseline recorded against the previous ones describes a bound that no longer
      // exists, and keeping it would let the first evaluation after the edit report a crossing of the old bound.
      algoAlertStateJpaRepository.deleteByIdAlgoStrategy(saved.getIdAlgoRuleStrategy());
      evaluationStateRepository.deleteByIdAlgoStrategy(saved.getIdAlgoRuleStrategy());
    }
    return saved;
  }

  @Autowired
  private AlgoTradingRepository tradingRepository;

  public int delEntityWithTenant(Integer idAlgoStrategy, Integer idTenant) {
    hierarchyWriteGuard.assertStrategyWritable(idAlgoStrategy);
    int deleted = algoStrategyJpaRepository.deleteByIdAlgoRuleStrategyAndIdTenant(idAlgoStrategy, idTenant);
    tradingRepository.clearRemovedAssignments(idTenant);
    return deleted;
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
