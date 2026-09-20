package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.AlgoAssetclassJpaRepositoryImpl;

/** The save path and generated forms share the same optional overrides and backend defaults. */
class AlgoRebalancingConfigurationTest {
  @Test
  void classSavePreservesExplicitZeroAndCanClearOverrides() {
    var repository = repository();
    var entity = new AlgoAssetclass();
    entity.setName("Bonds");
    entity.setSecurityDeviationPercentage(0.0);
    entity.setMaxTradedSecuritiesPerAssetclass(1);
    var saved = repository.saveOnlyAttributes(entity, null, Set.of());
    assertThat(saved.getSecurityDeviationPercentage()).isZero();
    assertThat(saved.getMaxTradedSecuritiesPerAssetclass()).isEqualTo(1);
    entity.setSecurityDeviationPercentage(null);
    entity.setMaxTradedSecuritiesPerAssetclass(null);
    saved = repository.saveOnlyAttributes(entity, entity, Set.of());
    assertThat(saved.getSecurityDeviationPercentage()).isNull();
    assertThat(saved.getMaxTradedSecuritiesPerAssetclass()).isNull();
  }

  @Test
  void directRepositoryWritesCannotBypassOverrideValidation() {
    var repository = repository();
    var entity = new AlgoAssetclass();
    entity.setName("Bonds");
    for (double invalid : new double[] { -1, 101, Double.NaN, Double.POSITIVE_INFINITY }) {
      entity.setSecurityDeviationPercentage(invalid);
      assertThatThrownBy(() -> repository.saveOnlyAttributes(entity, null, Set.of()))
          .isInstanceOf(DataViolationException.class);
    }
    entity.setSecurityDeviationPercentage(null);
    entity.setMaxTradedSecuritiesPerAssetclass(0);
    assertThatThrownBy(() -> repository.saveOnlyAttributes(entity, null, Set.of()))
        .isInstanceOf(DataViolationException.class);
  }

  @Test
  void strategyFormIncludesBackendDefaults() {
    var form = StrategyHelper
        .getFormDefinitionsByAlgoStrategyImpl(AlgoStrategyImplementationType.AS_HOLDING_TOP_REBALANCING);
    assertThat(form.defaultValues).containsEntry("securityDeviationPercentage", 5.0)
        .containsEntry("maxTradedSecuritiesPerAssetclass", 3);
    assertThat(form.topFormDefinitionList).extracting(field -> field.fieldName).contains("securityDeviationPercentage",
        "maxTradedSecuritiesPerAssetclass");
  }

  private AlgoAssetclassJpaRepositoryImpl repository() {
    var repository = new AlgoAssetclassJpaRepositoryImpl();
    var persistence = mock(AlgoAssetclassJpaRepository.class);
    when(persistence.save(any(AlgoAssetclass.class))).thenAnswer(call -> call.getArgument(0));
    ReflectionTestUtils.setField(repository, "algoAssetclassJpaRepository", persistence);
    ReflectionTestUtils.setField(repository, "hierarchyWriteGuard", mock(AlgoHierarchyWriteGuard.class));
    ReflectionTestUtils.setField(repository, "alertScopeLifecycle", mock(AlgoAlertScopeLifecycle.class));
    return repository;
  }
}
