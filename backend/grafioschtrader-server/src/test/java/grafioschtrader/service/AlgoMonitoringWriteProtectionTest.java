package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.*;

/** Generic edits cannot overwrite dedicated monitoring preferences, and deletion must respect the assignment. */
class AlgoMonitoringWriteProtectionTest {
  @Test
  void ordinaryStrategyEditsPreservePreferenceAndCreationDefaultsOn() {
    var implementation = new AlgoStrategyJpaRepositoryImpl();
    var repository = mock(AlgoStrategyJpaRepository.class);
    ReflectionTestUtils.setField(implementation, "algoStrategyJpaRepository", repository);
    ReflectionTestUtils.setField(implementation, "hierarchyWriteGuard", mock(AlgoHierarchyWriteGuard.class));
    ReflectionTestUtils.setField(implementation, "algoAlertStateJpaRepository",
        mock(AlgoAlertStateJpaRepository.class));
    ReflectionTestUtils.setField(implementation, "evaluationStateRepository",
        mock(AlgoAlertEvaluationStateJpaRepository.class));
    when(repository.getAlgoLevelType(10, 1)).thenReturn("T");
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    AlgoStrategy input = new AlgoStrategy();
    input.setIdTenant(1);
    input.setIdAlgoAssetclassSecurity(10);
    input.setAlgoStrategyImplementations(AlgoStrategyImplementationType.AS_HOLDING_TOP_REBALANCING);
    input.setStrategyConfig("{\"timePeriodPerYear\":4,\"thresholdPercentage\":2,\"securityDeviationPercentage\":5}");
    AlgoStrategy stored = new AlgoStrategy();
    stored.setAlertEnabled(false);
    assertThat(implementation.saveOnlyAttributes(input, stored, Set.of()).isAlertEnabled()).isFalse();
    input.setAlertEnabled(false);
    assertThat(implementation.saveOnlyAttributes(input, null, Set.of()).isAlertEnabled()).isTrue();
  }

  @Test
  void ordinaryTenantEditCannotClearOrReplaceMonitoring() {
    var implementation = new TenantJpaRepositoryImpl();
    var repository = mock(TenantJpaRepository.class);
    ReflectionTestUtils.setField(implementation, "tenantJpaRepository", repository);
    Tenant stored = new Tenant();
    stored.setIdTenant(1);
    stored.setCurrency("CHF");
    stored.setIdAlgoTop(10);
    Tenant submitted = new Tenant();
    submitted.setIdTenant(1);
    submitted.setCurrency("CHF");
    submitted.setIdAlgoTop(99);
    when(repository.getReferenceById(1)).thenReturn(stored);
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    assertThat(implementation.saveOnlyAttributes(submitted, stored, Set.of()).getIdAlgoTop()).isEqualTo(10);
  }

  @Test
  void assignedHierarchyMustBeUnassignedBeforeDeletion() {
    var implementation = new AlgoTopJpaRepositoryImpl();
    var tenants = mock(TenantJpaRepository.class);
    var tops = mock(AlgoTopJpaRepository.class);
    var trading = mock(AlgoTradingRepository.class);
    ReflectionTestUtils.setField(implementation, "monitoringTenants", tenants);
    ReflectionTestUtils.setField(implementation, "algoTopJpaRepository", tops);
    ReflectionTestUtils.setField(implementation, "tradingRepository", trading);
    ReflectionTestUtils.setField(implementation, "hierarchyWriteGuard", mock(AlgoHierarchyWriteGuard.class));
    Tenant tenant = new Tenant();
    tenant.setIdAlgoTop(10);
    when(tenants.lockMonitoringTenant(1)).thenReturn(Optional.of(tenant));
    assertThatThrownBy(() -> implementation.delEntityWithTenant(10, 1)).isInstanceOf(DataViolationException.class);
    verifyNoInteractions(tops, trading);
    tenant.setIdAlgoTop(null);
    implementation.delEntityWithTenant(10, 1);
    verify(tops).deleteByIdAlgoAssetclassSecurityAndIdTenant(10, 1);
    verify(trading).clearRemovedAssignments(1);
  }
}
