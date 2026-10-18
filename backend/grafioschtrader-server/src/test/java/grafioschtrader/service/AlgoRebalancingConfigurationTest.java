package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Set;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
      assertViolation(() -> repository.saveOnlyAttributes(entity, null, Set.of()), "security.deviation.percentage",
          "algo.rebalancing.invalid.band");
    }
    entity.setSecurityDeviationPercentage(null);
    entity.setMaxTradedSecuritiesPerAssetclass(0);
    assertViolation(() -> repository.saveOnlyAttributes(entity, null, Set.of()), "max.traded.securities.per.assetclass",
        "algo.rebalancing.invalid.limit");

    entity.setMaxTradedSecuritiesPerAssetclass(null);
    for (Double valid : new Double[] { 0d, 100d, null }) {
      entity.setSecurityDeviationPercentage(valid);
      assertThatCode(() -> repository.saveOnlyAttributes(entity, null, Set.of())).doesNotThrowAnyException();
    }
    entity.setSecurityDeviationPercentage(null);
    for (Integer valid : new Integer[] { 1, null }) {
      entity.setMaxTradedSecuritiesPerAssetclass(valid);
      assertThatCode(() -> repository.saveOnlyAttributes(entity, null, Set.of())).doesNotThrowAnyException();
    }
  }

  private static void assertViolation(ThrowingCallable call, String field,
      String messageKey) {
    assertThatThrownBy(call).isInstanceOfSatisfying(DataViolationException.class,
        error -> assertThat(error.getDataViolation()).singleElement().satisfies(violation -> {
          assertThat(violation.getField()).isEqualTo(field);
          assertThat(violation.getMessageKey()).isEqualTo(messageKey);
        }));
  }

  private AlgoAssetclassJpaRepositoryImpl repository() {
    var repository = new AlgoAssetclassJpaRepositoryImpl();
    var persistence = mock(AlgoAssetclassJpaRepository.class);
    when(persistence.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    ReflectionTestUtils.setField(repository, "algoAssetclassJpaRepository", persistence);
    ReflectionTestUtils.setField(repository, "hierarchyWriteGuard", mock(AlgoHierarchyWriteGuard.class));
    ReflectionTestUtils.setField(repository, "alertScopeLifecycle", mock(AlgoAlertScopeLifecycle.class));
    ReflectionTestUtils.setField(repository, "accountPriority", mock(AlgoAccountPriorityService.class));
    return repository;
  }
}
