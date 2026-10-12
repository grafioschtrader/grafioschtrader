package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.AlgoAssetclassJpaRepositoryImpl;

/** Direct repository writes enforce the bounds of optional rebalancing overrides. */
class AlgoRebalancingConfigurationTest {
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

  private AlgoAssetclassJpaRepositoryImpl repository() {
    var repository = new AlgoAssetclassJpaRepositoryImpl();
    var persistence = mock(AlgoAssetclassJpaRepository.class);
    ReflectionTestUtils.setField(repository, "algoAssetclassJpaRepository", persistence);
    ReflectionTestUtils.setField(repository, "hierarchyWriteGuard", mock(AlgoHierarchyWriteGuard.class));
    ReflectionTestUtils.setField(repository, "alertScopeLifecycle", mock(AlgoAlertScopeLifecycle.class));
    ReflectionTestUtils.setField(repository, "accountPriority", mock(AlgoAccountPriorityService.class));
    return repository;
  }
}
