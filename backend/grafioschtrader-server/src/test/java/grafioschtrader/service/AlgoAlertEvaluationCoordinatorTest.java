package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.config.FeatureConfig;
import grafioschtrader.entities.*;
import grafioschtrader.repository.*;

class AlgoAlertEvaluationCoordinatorTest {
  private final AlgoAlertScopeResolver resolver = mock(AlgoAlertScopeResolver.class);
  private final AlgoAlertEvaluationStateJpaRepository repository = mock(AlgoAlertEvaluationStateJpaRepository.class);
  private final AlgoAlertEvaluationStateService states = mock(AlgoAlertEvaluationStateService.class);
  private final AlgoAlertStateService crossings = mock(AlgoAlertStateService.class);
  private final SecurityJpaRepository securities = mock(SecurityJpaRepository.class);
  private final TradingDaysMinusJpaRepository holidays = mock(TradingDaysMinusJpaRepository.class);
  private final GlobalparametersService parameters = mock(GlobalparametersService.class);
  private final AlgoAlarmEvaluationService evaluator = mock(AlgoAlarmEvaluationService.class);
  private AlgoAlertEvaluationCoordinator coordinator;
  private final LocalDateTime now = LocalDateTime.parse("2026-09-04T12:00:00");
  private Security security;

  @BeforeEach
  void setup() {
    FeatureConfig features = mock(FeatureConfig.class);
    when(features.isAlgo()).thenReturn(true);
    when(features.isAlert()).thenReturn(true);
    when(parameters.getAlgoAlarmEvaluationIntervalHours()).thenReturn(4);
    when(states.claim(any(), any(), anyInt(), any(), anyBoolean(), anyBoolean())).thenReturn("claim");
    coordinator = new AlgoAlertEvaluationCoordinator(features, resolver, repository, states, crossings, securities,
        holidays, parameters, List.of(), evaluator);
    ReflectionTestUtils.setField(coordinator, "clock", Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    security = new Security();
    security.setIdSecuritycurrency(10);
    security.setSLast(95.0);
    security.setSTimestamp(now.minusHours(5));
    Stockexchange exchange = new Stockexchange();
    exchange.setIdStockexchange(20);
    exchange.setTimeZone("UTC");
    exchange.setTimeOpen(LocalTime.of(8, 0));
    exchange.setTimeClose(LocalTime.of(17, 0));
    security.setStockexchange(exchange);
  }

  private AlgoAlertScope scope(int tenant, int strategy, AlgoStrategyImplementationType type) {
    AlgoStrategy alert = new AlgoStrategy();
    alert.setIdAlgoRuleStrategy(strategy);
    alert.setAlgoStrategyImplementations(type);
    return new AlgoAlertScope(tenant, alert, security, "test", true);
  }

  @Test
  void allSixTypesShareOneRefreshAcrossTenants() {
    List<AlgoAlertScope> scopes = new ArrayList<>();
    for (AlgoStrategyImplementationType type : List.of(
        AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE,
        AlgoStrategyImplementationType.AS_HOLDING_TOP_GAIN_LOSE,
        AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_PERIOD_PRICE_GAIN_LOSE_PERCENT,
        AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_MA_CROSSING,
        AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_RSI_THRESHOLD,
        AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_EXPRESSION)) {
      scopes.add(scope(scopes.size() + 1, scopes.size() + 100, type));
    }
    when(resolver.resolveAll()).thenReturn(scopes);
    when(securities.updateLastPriceByList(anyList())).thenAnswer(_ -> {
      security.setSTimestamp(now);
      security.setSLast(105.0);
      return List.of(security);
    });
    doAnswer(call -> {
      ((Runnable) call.getArgument(5)).run();
      return null;
    }).when(states).finish(any(), anyString(), any(), any(), isNull(), any(Runnable.class));
    coordinator.background();
    verify(securities, times(1)).updateLastPriceByList(anyList());
    verify(states, times(6)).finish(any(), eq("claim"), eq(now), eq(now), isNull(), any());
  }

  @Test
  void staleReturnedQuoteIsUnavailableAndCannotAdvanceBaseline() {
    when(resolver.resolveAll())
        .thenReturn(List.of(scope(1, 100, AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE)));
    when(securities.updateLastPriceByList(anyList())).thenReturn(List.of(security));
    coordinator.background();
    verify(states).finish(any(), eq("claim"), eq(now), eq(security.getSTimestamp()),
        eq("Required quote is missing or stale"), any());
    verifyNoInteractions(evaluator);
  }

  @Test
  void scanDoesNotDownloadAndRecentAttemptAvoidsEnqueue() {
    AlgoAlertScope scope = scope(1, 100, AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE);
    when(resolver.resolveAll()).thenReturn(List.of(scope));
    assertThat(coordinator.hasDueAlerts()).isTrue();
    AlgoAlertEvaluationState state = new AlgoAlertEvaluationState();
    state.setConfigFingerprint(
        grafioschtrader.algo.strategy.model.alerts.AlertConfigAdapter.fingerprint(scope.strategy()));
    state.setLastAttempt(now.minusMinutes(5));
    state.setIdTenant(1);
    state.setIdAlgoStrategy(100);
    state.setIdSecuritycurrency(10);
    when(repository.findAll()).thenReturn(List.of(state));
    assertThat(coordinator.hasDueAlerts()).isFalse();
    verifyNoInteractions(securities, evaluator);
  }

  @Test
  void manualUsesOnlyCallerTenantAndBypassesWeekend() {
    ReflectionTestUtils.setField(coordinator, "clock",
        Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC));
    when(resolver.resolveForTenant(7))
        .thenReturn(List.of(scope(7, 100, AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE)));
    coordinator.manual(7);
    verify(resolver).resolveForTenant(7);
    verify(resolver, never()).resolveAll();
    verify(states).claim(any(), any(), eq(4), any(), eq(false), eq(false));
  }

  @Test
  void weekendDoesNotDownloadOrClaim() {
    ReflectionTestUtils.setField(coordinator, "clock",
        Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC));
    when(resolver.resolveAll())
        .thenReturn(List.of(scope(1, 100, AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE)));
    coordinator.background();
    verifyNoInteractions(states, securities);
  }
}
