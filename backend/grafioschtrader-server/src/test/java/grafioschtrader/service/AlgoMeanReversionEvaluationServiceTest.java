package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.config.FeatureConfig;
import grafioschtrader.entities.*;
import grafioschtrader.repository.*;
import grafioschtrader.service.AlgoMeanReversionDecisionService.Position;
import grafioschtrader.types.*;

/** Live proposals reserve shared equity, but never act as transactions or release exposure. */
class AlgoMeanReversionEvaluationServiceTest {
  private final AlgoTopJpaRepository tops = mock(AlgoTopJpaRepository.class);
  private final AlgoAssetclassJpaRepository buckets = mock(AlgoAssetclassJpaRepository.class);
  private final AlgoSecurityJpaRepository members = mock(AlgoSecurityJpaRepository.class);
  private final AlgoAlertScopeResolver scopes = mock(AlgoAlertScopeResolver.class);
  private final AlgoTradingRepository data = mock(AlgoTradingRepository.class);
  private final AlgoHistoricalValuationService valuation = mock(AlgoHistoricalValuationService.class);
  private final AlgoMeanReversionPositionService positions = mock(AlgoMeanReversionPositionService.class);
  private final AlgoRecommendationJpaRepository recommendations = mock(AlgoRecommendationJpaRepository.class);
  private final AlgoAlarmRecorder recorder = mock(AlgoAlarmRecorder.class);
  private final SimulationSourceRepository source = mock(SimulationSourceRepository.class);
  private final WatchlistJpaRepository watchlists = mock(WatchlistJpaRepository.class);
  private final FeatureConfig features = mock(FeatureConfig.class);
  private final LocalDate day = LocalDate.of(2026, 9, 7);
  private final Security security = mock(Security.class);
  private final AlgoTop top = mock(AlgoTop.class);
  private final AlgoReplayCalendar calendar = new AlgoReplayCalendar(mock(TradingDaysMinusJpaRepository.class),
      mock(TradingDaysPlusJpaRepository.class));
  private final AlgoMeanReversionScopeEvaluator evaluator = new AlgoMeanReversionScopeEvaluator(tops, buckets, members,
      scopes, valuation, positions, new AlgoMeanReversionDecisionService(new AlgoScaleOutModule()), source, watchlists,
      calendar);
  private final AlgoMeanReversionEvaluationService service = new AlgoMeanReversionEvaluationService(tops, scopes, data,
      recommendations, recorder, features, evaluator);
  private final List<AlgoRecommendation> saved = new ArrayList<>();

  void setup() throws Exception {
    ReflectionTestUtils.setField(service, "clock",
        Clock.fixed(day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    when(features.isAlgo()).thenReturn(true);
    when(features.isAlert()).thenReturn(true);
    Tenant tenant = new Tenant();
    tenant.setIdTenant(1);
    tenant.setCurrency("CHF");
    when(data.lockTenant(1)).thenReturn(tenant);
    when(top.getId()).thenReturn(10);
    when(top.getIdWatchlist()).thenReturn(20);
    when(top.getPercentage()).thenReturn(100f);
    when(tops.findByIdTenantOrderByName(1)).thenReturn(List.of(top));
    AlgoAssetclass bucket = mock(AlgoAssetclass.class);
    when(bucket.getId()).thenReturn(11);
    when(bucket.getPercentage()).thenReturn(100f);
    when(bucket.isActivatable()).thenReturn(true);
    when(buckets.findByIdTenantAndIdAlgoAssetclassParent(1, 10)).thenReturn(List.of(bucket));
    AlgoSecurity member = mock(AlgoSecurity.class);
    when(member.getId()).thenReturn(12);
    when(member.getSecurity()).thenReturn(security);
    when(member.getPercentage()).thenReturn(100f);
    when(member.isActivatable()).thenReturn(true);
    when(members.findByIdAlgoSecurityParentAndIdTenant(11, 1)).thenReturn(List.of(member));
    when(security.getId()).thenReturn(3);
    when(security.getCurrency()).thenReturn("CHF");
    when(security.getStockexchange()).thenReturn(mock(Stockexchange.class));
    when(watchlists.securitiesOfWatchlist(20)).thenReturn(List.of(security));
    when(data.history(eq(3), any(), any())).thenReturn(List.of(new Historyquote(3, day, 80),
        new Historyquote(3, day.minusDays(1), 100), new Historyquote(3, day.minusDays(2), 100)));
    when(valuation.value(eq(1), eq(day), any(), any())).thenReturn(new AlgoHistoricalValuationService.Snapshot("CHF",
        List.of(), Map.of(), Map.of("CHF", 1.0), 1000, 0, List.of()));
    when(positions.reconcile(eq(1), anyInt(), eq(security), eq(day))).thenReturn(Position.empty());
    when(positions.assignmentResolved(1, security, day)).thenReturn(true);
    when(recommendations.save(any())).thenAnswer(call -> {
      AlgoRecommendation r = call.getArgument(0);
      saved.add(r);
      return r;
    });
  }

  AlgoAlertScope scope(int id) throws Exception {
    AlgoStrategy strategy = new AlgoStrategy();
    strategy.setIdAlgoRuleStrategy(id);
    strategy.setAlgoStrategyImplementations(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP);
    var config = AlgoMeanReversionDecisionServiceTest.config();
    config.entry.initial_buy_sizing.pct = .6;
    config.risk_controls.max_position_exposure_pct = 1.0;
    strategy.setStrategyConfig(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(config));
    return new AlgoAlertScope(1, strategy, security, "Mean reversion", true);
  }

  @Test
  void additionsReserveCapacityAndCannotFollowAnotherStrategysExit() throws Exception {
    setup();
    var first = scope(5);
    var config = AlgoMeanReversionDecisionServiceTest.config("/testdata/average-down-strategy.json");
    config.downside_management.variant_B_average_down.add_sizing.amount = 600.0;
    config.risk_controls.max_position_exposure_pct = 1.0;
    first.strategy()
        .setStrategyConfig(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(config));
    var second = scope(6);
    second.strategy().setStrategyConfig(first.strategy().getStrategyConfig());
    when(scopes.resolveForAlgoTop(top)).thenReturn(List.of(first, second));
    when(positions.reconcile(eq(1), anyInt(), eq(security), eq(day)))
        .thenReturn(new Position(42, 1, 1, 0, 100, 100, day.minusDays(3), null, 1));
    service.evaluate(1, true);
    assertEquals(List.of("AVERAGE_DOWN_ADD", "MEAN_REVERSION_BUDGET_EXHAUSTED"),
        saved.stream().sorted(Comparator.comparing(AlgoRecommendation::getIdAlgoStrategy))
            .map(AlgoRecommendation::getRationale).toList());
    saved.clear();
    when(scopes.resolveForAlgoTop(top)).thenReturn(List.of(first, scope(6)));
    service.evaluate(1, true);
    assertEquals(List.of("MEAN_REVERSION_STOP_EXIT", "MEAN_REVERSION_PENDING_EXIT"),
        saved.stream().map(AlgoRecommendation::getRationale).toList());
  }

  @Test
  void additionFillsRecheckDrawdownCountAndAllowRemainderOfCountedSignal() throws Exception {
    setup();
    var scope = scope(5);
    var config = AlgoMeanReversionDecisionServiceTest.config("/testdata/average-down-strategy.json");
    config.risk_controls.max_position_exposure_pct = 1.0;
    scope.strategy()
        .setStrategyConfig(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(config));
    when(scopes.resolveForAlgoTop(top)).thenReturn(List.of(scope));
    var budgets = new AlgoMeanReversionFillBudgetService(tops, buckets, members, scopes, valuation, source);
    var current = new Position(42, 1, 1, 0, 100, 100, day.minusDays(3), null, 1, 3, List.of());
    var context = new AlgoMeanReversionDecisionService.Context(1, 5, 3, day.minusDays(1), (_, _) -> List.of(), current,
        1000, 1000, 1000, 1000, 100, 80, true);
    var decision = new AlgoMeanReversionDecisionService.Decision(AlgoMeanReversionDecisionService.Action.ADD, 1, 2, 80,
        "AVERAGE_DOWN_ADD", "add", "");
    Transaction fill = new Transaction();
    fill.setSecuritycurrency(security);
    fill.setUnits(1.0);
    fill.setQuotation(80.0);
    fill.setTransactionTime(day.atTime(12, 0));
    assertThrows(IllegalArgumentException.class, () -> budgets.validate(1, context, decision, fill, current, 0));
    assertDoesNotThrow(() -> budgets.validate(1, context, decision, fill, current, 1));
    fill.setQuotation(74.0);
    assertThrows(IllegalArgumentException.class, () -> budgets.validate(1, context, decision, fill, current, 1));
  }

  @Test
  void concurrentStrategiesShareReservedBudget() throws Exception {
    setup();
    when(scopes.resolveForAlgoTop(top)).thenReturn(List.of(scope(5), scope(6)));
    service.evaluate(1, true);
    assertEquals(List.of("MEAN_REVERSION_ENTRY", "MEAN_REVERSION_BUDGET_EXHAUSTED"),
        saved.stream().sorted(Comparator.comparing(AlgoRecommendation::getIdAlgoStrategy))
            .map(AlgoRecommendation::getRationale).toList());
    verify(recorder, times(1)).record(any(), eq(AlgoSignalKind.ENTRY_SIGNAL), anyByte(), anyString(), anyString(),
        eq(day));
  }

  @Test
  void exitsSuppressConflictingEntriesWithoutInventingFreedBudget() throws Exception {
    setup();
    when(scopes.resolveForAlgoTop(top)).thenReturn(List.of(scope(5), scope(6)));
    when(positions.reconcile(1, 6, security, day))
        .thenReturn(new Position(42, 2, 2, 0, 100, 100, day.minusDays(3), null, 1));
    service.evaluate(1, true);
    assertEquals(List.of("MEAN_REVERSION_STOP_EXIT", "MEAN_REVERSION_PENDING_EXIT"),
        saved.stream().map(AlgoRecommendation::getRationale).toList());
    assertEquals(0, saved.getLast().getRecommendedUnits());
    verify(recorder, never()).record(any(), eq(AlgoSignalKind.ENTRY_SIGNAL), anyByte(), anyString(), anyString(),
        any());
  }

  @Test
  void missingCompletedQuoteProducesDiagnosticWithoutSignal() throws Exception {
    setup();
    when(scopes.resolveForAlgoTop(top)).thenReturn(List.of(scope(5)));
    when(data.history(eq(3), any(), any())).thenReturn(List.of(new Historyquote(3, day.minusDays(1), 80)));
    service.evaluate(1, true);
    assertEquals(AlgoRecommendationAction.REBALANCE_BLOCKED, saved.getFirst().getRecommendedAction());
    assertTrue(saved.getFirst().getRationale().contains("MEAN_REVERSION_NO_CLOSE"));
    verifyNoInteractions(recorder);
  }

  @Test
  void simulationFillRechecksActualPriceAndRemainingParentCapacity() throws Exception {
    setup();
    when(scopes.resolveForAlgoTop(top)).thenReturn(List.of(scope(5)));
    var budgets = new AlgoMeanReversionFillBudgetService(tops, buckets, members, scopes, valuation, source);
    var context = new AlgoMeanReversionDecisionService.Context(1, 5, 3, day.minusDays(1), (_, _) -> List.of(),
        Position.empty(), 1000, 1000, 1000, 1000, 0, 80, true);
    var decision = new AlgoMeanReversionDecisionService.Decision(AlgoMeanReversionDecisionService.Action.ENTRY, 1, 10,
        80, "MEAN_REVERSION_ENTRY", "signal", AlgoMessageAlert.NO_TRANCHE);
    Transaction fill = new Transaction();
    fill.setSecuritycurrency(security);
    fill.setUnits(10.0);
    fill.setQuotation(80.0);
    fill.setTransactionTime(day.atTime(12, 0));
    fill.setTransactionType(TransactionType.ACCUMULATE);
    assertDoesNotThrow(() -> budgets.validate(1, context, decision, fill, context.position(), 0));
    fill.setQuotation(110.0);
    assertThrows(IllegalArgumentException.class,
        () -> budgets.validate(1, context, decision, fill, context.position(), 0));
    fill.setQuotation(80.0);
    when(valuation.value(eq(1), eq(day), any(), any())).thenReturn(new AlgoHistoricalValuationService.Snapshot("CHF",
        List.of(), Map.of(), Map.of("CHF", 1.0), 1000, 500, List.of()));
    assertThrows(IllegalArgumentException.class,
        () -> budgets.validate(1, context, decision, fill, context.position(), 0));
  }
}
