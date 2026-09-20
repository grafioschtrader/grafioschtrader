package grafioschtrader.service;

import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import grafioschtrader.entities.*;
import grafioschtrader.repository.AlgoAlertEvaluationStateJpaRepository;

class AlgoAlertScopeLifecycleTest {
  @Test
  void disableBetweenScansResetsOnlyAffectedPairs() {
    AlgoAlertScopeResolver resolver = mock(AlgoAlertScopeResolver.class);
    AlgoAlertEvaluationStateJpaRepository repository = mock(AlgoAlertEvaluationStateJpaRepository.class);
    AlgoAlertStateService crossings = mock(AlgoAlertStateService.class);
    AlgoAlertScopeLifecycle lifecycle = new AlgoAlertScopeLifecycle(resolver, repository, crossings);
    AlgoStrategy strategy = new AlgoStrategy();
    strategy.setIdAlgoRuleStrategy(2);
    Security security = new Security();
    security.setIdSecuritycurrency(3);
    AlgoAlertScope enabled = new AlgoAlertScope(1, strategy, security, "test", true);
    AlgoAlertScope disabled = new AlgoAlertScope(1, strategy, security, "test", false);
    when(resolver.resolveForTenant(1)).thenReturn(List.of(enabled)).thenReturn(List.of(disabled));
    AlgoAlertEvaluationState stored = new AlgoAlertEvaluationState();
    when(repository.findByIdTenantAndIdAlgoStrategyAndIdSecuritycurrency(1, 2, 3)).thenReturn(Optional.of(stored));
    var before = lifecycle.snapshot(1);
    lifecycle.changed(1, before);
    verify(crossings).discard(2, 3);
    verify(repository).delete(stored);
  }
}
