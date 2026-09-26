package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.algo.RebalancingPlan;
import grafioschtrader.entities.*;
import grafioschtrader.types.*;

/** Exercises the replay checkpoint with the same executable lines used for live recommendations, without a database. */
class AlgoReplayRebalancingTest {
  private static final LocalDate DAY = LocalDate.of(2020, 2, 7);
  private static final LocalDate NEXT = DAY.plusDays(3);
  private final AlgoHistoricalReplayService replay = new AlgoHistoricalReplayService();
  private final AlgoReplayState state = mock(AlgoReplayState.class);
  private final AlgoRebalancingService rebalancing = mock(AlgoRebalancingService.class);
  private final AlgoReplayBooking booking = mock(AlgoReplayBooking.class);
  private final AlgoReplayMarketData market = mock(AlgoReplayMarketData.class);
  private final AlgoTop top = new AlgoTop();
  private final Map<Integer, Security> securities = new LinkedHashMap<>();

  @BeforeEach
  void setup() {
    top.setIdAlgoAssetclassSecurity(237);
    Tenant tenant = new Tenant();
    tenant.setCurrency("CHF");
    ReflectionTestUtils.setField(replay, "rebalancing", rebalancing);
    ReflectionTestUtils.setField(replay, "booking", booking);
    AlgoReplayCalendar calendar = mock(AlgoReplayCalendar.class);
    ReflectionTestUtils.setField(replay, "calendar", calendar);
    when(calendar.isMarketFillDate(any(), eq(DAY), any(), any(), any())).thenReturn(true);
    when(calendar.isMarketFillDate(any(), eq(NEXT), any(), any(), any())).thenReturn(true);
    // A mock skips field initializers, so the collection the replay keeps across days is supplied here.
    ReflectionTestUtils.setField(state, "rebalanceFollowUpClasses", new HashSet<Integer>());
    ReflectionTestUtils.setField(state, "rebalancingConfigured", true);
    ReflectionTestUtils.setField(state, "algoTop", top);
    ReflectionTestUtils.setField(state, "tenant", tenant);
    ReflectionTestUtils.setField(state, "market", market);
    ReflectionTestUtils.setField(state, "locale", Locale.ROOT);
    ReflectionTestUtils.setField(state, "securities", securities);
    ReflectionTestUtils.setField(state, "fx",
        new AlgoReplayFx(AlgoReplayFxTest.snapshot(Map.of()), "CHF", (_, _, _) -> 1.0, _ -> {
        }));
    ReflectionTestUtils.setField(state, "inputs",
        new AlgoReplayInputs.Snapshot(2, false, false, 0, Map.of(), Map.of(), Map.of(), List.of()));
    when(state.idTenant()).thenReturn(65);
    when(rebalancing.isCheckpointDue(eq(65), same(top), eq(DAY), isNull())).thenReturn(true);
  }

  @Test
  void cappedPlanExecutesOnlySelectedNamesAndReportsRoundingAndFailures() throws Exception {
    var first = line(1, 238, 7_000, AlgoRecommendationAction.REBALANCE_BUY);
    var second = line(2, 238, 7_000, AlgoRecommendationAction.REBALANCE_BUY);
    var third = line(3, 238, 7_000, AlgoRecommendationAction.REBALANCE_BUY);
    stubPlan(List.of(first, second, third),
        List.of(new RebalancingPlan.ClassAdjustment(238, -37.5, 5, 3, 30_000, 21_000, 9_000, "REBALANCE_TRADE_LIMIT")));
    when(booking.book(same(state), same(securities.get(1)), isNull(), eq(7.0), eq(TransactionType.ACCUMULATE), eq(DAY)))
        .thenReturn(fill(6));
    when(booking.book(same(state), same(securities.get(2)), isNull(), eq(7.0), eq(TransactionType.ACCUMULATE), eq(DAY)))
        .thenThrow(new IllegalArgumentException("REPLAY_MARGIN_UNSUPPORTED"));
    when(booking.book(same(state), same(securities.get(3)), isNull(), eq(7.0), eq(TransactionType.ACCUMULATE), eq(DAY)))
        .thenReturn(fill(7));
    ReflectionTestUtils.invokeMethod(replay, "rebalance", state, DAY);
    verify(booking, times(3)).book(same(state), any(), isNull(), anyDouble(), any(), eq(DAY));
    verify(state).write(eq(AlgoEventType.UNAVAILABLE), eq(DAY), isNull(), isNull(), isNull(), isNull(), eq(17_000.0),
        eq("CHF"), eq("REBALANCE_RESIDUAL"), contains("executed=13000.00"));
    assertThat(state.lastRebalancedOn).isEqualTo(DAY);
  }

  @Test
  void checkpointWithNoExecutableLinesStillReportsResidualAndAdvances() {
    stubPlan(List.of(), List.of(
        new RebalancingPlan.ClassAdjustment(238, -30, 5, 3, 24_000, 0, 24_000, "REBALANCE_BAND_OR_ELIGIBILITY_LIMIT")));
    ReflectionTestUtils.invokeMethod(replay, "rebalance", state, DAY);
    verifyNoInteractions(booking);
    verify(state).write(eq(AlgoEventType.UNAVAILABLE), eq(DAY), isNull(), isNull(), isNull(), isNull(), eq(24_000.0),
        eq("CHF"), eq("REBALANCE_RESIDUAL"), anyString());
    assertThat(state.lastRebalancedOn).isEqualTo(DAY);
  }

  @Test
  void shortCoverReducesExposureAndExecutesBeforeLongPurchase() throws Exception {
    var increase = line(1, 238, 2_000, AlgoRecommendationAction.REBALANCE_BUY);
    var reduction = line(2, 239, -3_000, AlgoRecommendationAction.REBALANCE_BUY);
    stubPlan(List.of(increase, reduction), List.of());
    when(booking.book(same(state), any(), isNull(), anyDouble(), any(), eq(DAY))).thenReturn(fill(2));
    ReflectionTestUtils.invokeMethod(replay, "rebalance", state, DAY);
    var order = inOrder(booking);
    order.verify(booking).book(state, securities.get(2), null, 3, TransactionType.ACCUMULATE, DAY);
    order.verify(booking).book(state, securities.get(1), null, 2, TransactionType.ACCUMULATE, DAY);
  }

  @Test
  void unpaidPurchaseIsCompletedOnTheFollowingDayWithoutMovingTheCheckpoint() throws Exception {
    var buy = line(1, 238, 30_000, AlgoRecommendationAction.REBALANCE_BUY);
    var sell = line(2, 239, -30_000, AlgoRecommendationAction.REBALANCE_SELL);
    stubPlan(List.of(buy, sell), List.of(new RebalancingPlan.ClassAdjustment(238, -12, 5, 3, 30_000, 30_000, 0, null),
        new RebalancingPlan.ClassAdjustment(239, 12, 5, 3, -30_000, -30_000, 0, null)));
    // The sale settles, but its proceeds are not spendable yet: the purchase is cut down to a fifth.
    when(booking.book(same(state), same(securities.get(2)), isNull(), eq(30.0), eq(TransactionType.REDUCE), eq(DAY)))
        .thenReturn(fill(30));
    when(
        booking.book(same(state), same(securities.get(1)), isNull(), eq(30.0), eq(TransactionType.ACCUMULATE), eq(DAY)))
            .thenReturn(fill(6));
    ReflectionTestUtils.invokeMethod(replay, "rebalance", state, DAY);
    assertThat(state.rebalanceFollowUpClasses).containsExactly(238);

    var rest = line(1, 238, 24_000, AlgoRecommendationAction.REBALANCE_BUY);
    var sellAgain = line(2, 239, -5_000, AlgoRecommendationAction.REBALANCE_SELL);
    var followUp = new RebalancingPlan(65, 237, 6, "Example", NEXT, "CHF", 100_000, 50_000, 50_000, 80_000, 0, 80, 5,
        false, false, true, List.of(rest, sellAgain),
        List.of(new RebalancingPlan.ClassAdjustment(238, -9.6, 5, 3, 24_000, 24_000, 0, null)));
    when(rebalancing.isCheckpointDue(eq(65), same(top), eq(NEXT), eq(DAY))).thenReturn(false);
    when(rebalancing.followUpPlan(eq(65), same(top), eq(NEXT), eq(Locale.ROOT), eq(DAY), same(market), eq(Set.of(238))))
        .thenReturn(followUp);
    when(booking.book(same(state), same(securities.get(1)), isNull(), eq(24.0), eq(TransactionType.ACCUMULATE),
        eq(NEXT))).thenReturn(fill(24));
    ReflectionTestUtils.invokeMethod(replay, "rebalance", state, NEXT);

    verify(state).write(eq(AlgoEventType.REBALANCE_PLAN), eq(NEXT), isNull(), isNull(), isNull(), isNull(), isNull(),
        eq("CHF"), eq("REPLAY_REBALANCE_FOLLOW_UP"));
    verify(booking, never()).book(any(), same(securities.get(2)), any(), anyDouble(), any(), eq(NEXT));
    assertThat(state.lastRebalancedOn).isEqualTo(DAY);
    assertThat(state.rebalanceFollowUpClasses).isEmpty();
  }

  private RebalancingPlan.Line line(int id, int bucket, double change, AlgoRecommendationAction action) {
    Security security = new Security();
    security.setIdSecuritycurrency(id);
    securities.put(id, security);
    return new RebalancingPlan.Line("S", id + 100, bucket, id, "Instrument", false, 8.0, 5.0, -3.0, 8_000.0, 5_000.0,
        action, Math.abs(change), Math.abs(change) / 1_000, "REBALANCE_CLASS_INCREASE", -3.75, change);
  }

  private void stubPlan(List<RebalancingPlan.Line> lines, List<RebalancingPlan.ClassAdjustment> adjustments) {
    var plan = new RebalancingPlan(65, 237, 6, "Example", DAY, "CHF", 100_000, 50_000, 50_000, 80_000, 0, 80, 5, false,
        true, true, lines, adjustments);
    when(rebalancing.plan(eq(65), same(top), eq(DAY), eq(Locale.ROOT), isNull(), same(market))).thenReturn(plan);
  }

  private Transaction fill(double units) {
    Transaction transaction = new Transaction();
    transaction.setUnits(units);
    transaction.setQuotation(1_000.0);
    return transaction;
  }
}
