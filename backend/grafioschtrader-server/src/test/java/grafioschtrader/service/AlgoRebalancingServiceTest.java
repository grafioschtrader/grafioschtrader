package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.algo.RebalancingPlan;
import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.config.FeatureConfig;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoRecommendation;
import grafioschtrader.entities.AlgoRuleStrategy.AlgoRuleStrategyParam;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.AlgoRecommendationJpaRepository;
import grafioschtrader.repository.AlgoSecurityJpaRepository;
import grafioschtrader.repository.AlgoStrategyJpaRepository;
import grafioschtrader.repository.AlgoTopJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.service.AlgoHistoricalValuationService.Position;
import grafioschtrader.service.AlgoHistoricalValuationService.Snapshot;
import grafioschtrader.types.AlgoRebalancingTrigger;
import grafioschtrader.types.AlgoRecommendationAction;

/**
 * The allocation arithmetic of the rebalancing, against hand-calculated numbers.
 *
 * <p>
 * The valuation itself is mocked rather than reproduced: what has to be proven here is that parent-relative weights are
 * resolved against total net equity exactly once, that the direction and the blocking rules follow from that, and that
 * a book which cannot be represented as an allocation is still reported instead of refused. How a snapshot is built
 * from transactions and prices is the subject of {@link AlgoHistoricalValuationServiceTest}.
 * </p>
 */
class AlgoRebalancingServiceTest {

  private static final LocalDate VALUATION_DATE = LocalDate.of(2026, 6, 15);
  private static final int ID_TENANT = 1;
  private static final int ID_ALGO_TOP = 10;
  private static final int ID_BUCKET = 20;
  private static final int ID_MEMBER = 30;
  private static final int ID_SECURITY = 40;

  private final AlgoRebalancingService service = new AlgoRebalancingService();
  private final AlgoHistoricalValuationService valuation = mock(AlgoHistoricalValuationService.class);
  private final AlgoTopJpaRepository algoTops = mock(AlgoTopJpaRepository.class);
  private final AlgoAssetclassJpaRepository buckets = mock(AlgoAssetclassJpaRepository.class);
  private final AlgoSecurityJpaRepository members = mock(AlgoSecurityJpaRepository.class);
  private final AlgoStrategyJpaRepository strategies = mock(AlgoStrategyJpaRepository.class);
  private final AlgoRecommendationJpaRepository recommendations = mock(AlgoRecommendationJpaRepository.class);
  private final HistoryquoteJpaRepository quotes = mock(HistoryquoteJpaRepository.class);
  private final AlgoRecommendationWriter writer = mock(AlgoRecommendationWriter.class);

  private final AlgoStrategy rebalancing = strategy(100, AlgoStrategyImplementationType.AS_HOLDING_TOP_REBALANCING, 4,
      2);
  private AlgoTop algoTop = algoTop(48.2f);
  private float bucketPercentage = 100f;
  private final Security security = security(ID_SECURITY, "World ETF", "CHF");

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(service, "valuation", valuation);
    ReflectionTestUtils.setField(service, "algoTopJpaRepository", algoTops);
    ReflectionTestUtils.setField(service, "algoAssetclassJpaRepository", buckets);
    ReflectionTestUtils.setField(service, "algoSecurityJpaRepository", members);
    ReflectionTestUtils.setField(service, "algoStrategyJpaRepository", strategies);
    ReflectionTestUtils.setField(service, "algoRecommendationJpaRepository", recommendations);
    ReflectionTestUtils.setField(service, "historyquoteJpaRepository", quotes);
    ReflectionTestUtils.setField(service, "algoRecommendationWriter", writer);
    ReflectionTestUtils.setField(service, "algoAlarmRecorder", mock(AlgoAlarmRecorder.class));
    ReflectionTestUtils.setField(service, "features", mock(FeatureConfig.class));
    MessageSource messages = mock(MessageSource.class);
    when(messages.getMessage(any(String.class), any(), any(String.class), any())).thenAnswer(i -> i.getArgument(0));
    ReflectionTestUtils.setField(service, "messageSource", messages);
    when(strategies.findByIdAlgoAssetclassSecurityAndIdTenant(ID_ALGO_TOP, ID_TENANT)).thenReturn(List.of(rebalancing));
    when(strategies.findByIdAlgoAssetclassSecurityAndIdTenant(ID_BUCKET, ID_TENANT)).thenReturn(List.of());
    when(strategies.findByIdAlgoAssetclassSecurityAndIdTenant(ID_MEMBER, ID_TENANT)).thenReturn(List.of());
    when(recommendations.findLastCheckpointDate(anyInt(), anyInt())).thenReturn(Optional.of(VALUATION_DATE));
    when(quotes.getIdDateCloseByIdsAndDate(any(), any())).thenReturn(List.of());
  }

  @Test
  @DisplayName("A 50% bucket of a 48.2% ceiling is 24.1% of equity, and its 60% security 14.46%")
  void parentRelativeWeightsResolveAgainstTotalEquity() {
    // The example of the requirements, with the siblings that make its weights add up: two buckets of 50%, and
    // inside the first one a 60/40 split.
    hierarchy(48.2f, 50f, 60f);
    AlgoAssetclass sibling = new AlgoAssetclass();
    sibling.setIdAlgoAssetclassSecurity(21);
    sibling.setIdTenant(ID_TENANT);
    sibling.setPercentage(50f);
    sibling.setName("Strategic bonds");
    List<AlgoAssetclass> siblingBuckets = List.of(bucketNode(), sibling);
    List<AlgoSecurity> splitMembers = List.of(memberNode(60f), otherMember(40f));
    when(buckets.findByIdTenantAndIdAlgoAssetclassParent(ID_TENANT, ID_ALGO_TOP)).thenReturn(siblingBuckets);
    when(strategies.findByIdAlgoAssetclassSecurityAndIdTenant(21, ID_TENANT)).thenReturn(List.of());
    when(strategies.findByIdAlgoAssetclassSecurityAndIdTenant(31, ID_TENANT)).thenReturn(List.of());
    when(members.findByIdAlgoSecurityParentAndIdTenant(21, ID_TENANT)).thenReturn(List.of());
    when(members.findByIdAlgoSecurityParentAndIdTenant(ID_BUCKET, ID_TENANT)).thenReturn(splitMembers);
    snapshot(100_000, 0, List.of());

    RebalancingPlan plan = plan();

    // The weights are a float column, so 48.2 is not exactly 48.2 and the amounts carry that. A thousandth of a
    // currency unit on a hundred thousand is below anything the report shows, but it is not zero.
    Offset<Double> rounding = Offset.offset(0.01);
    assertThat(plan.investmentBudget()).isCloseTo(48_200.0, rounding);
    RebalancingPlan.Line bucket = lineOf(plan, StrategyHelper.ASSET_CLASS_LEVEL_LETTER);
    assertThat(bucket.targetPercentage()).isCloseTo(24.1, rounding);
    assertThat(bucket.targetAmount()).isCloseTo(24_100.0, rounding);
    RebalancingPlan.Line instrument = lineOf(plan, StrategyHelper.SECURITY_LEVEL_LETTER);
    assertThat(instrument.targetPercentage()).isCloseTo(14.46, rounding);
    assertThat(instrument.targetAmount()).isCloseTo(14_460.0, rounding);
  }

  @Test
  void replayUsesRedistributedWeightsWhileTheLiveHierarchyRetainsItsAllocation() {
    hierarchy(80f, 100f, 25f);
    var bucket = bucketNode();
    var permitted = memberNode(25f);
    var excluded = otherMember(75f);
    var children = List.of(permitted, excluded);
    when(members.findByIdAlgoSecurityParentAndIdTenant(ID_BUCKET, ID_TENANT)).thenReturn(children);
    when(strategies.findByIdAlgoAssetclassSecurityAndIdTenant(31, ID_TENANT)).thenReturn(List.of());
    var effective = AlgoReplayAllocation.capture(algoTop, List.of(bucket), _ -> children, s -> s.getId().equals(41));
    var market = mock(AlgoHistoricalValuationService.ClosingPrices.class);
    when(market.allocation()).thenReturn(effective);
    when(market.close(any(), any())).thenReturn(100d);
    snapshot(100_000, 0, List.of());
    var replay = service.initialPlan(ID_TENANT, algoTop, VALUATION_DATE, Locale.ROOT, market);
    assertThat(replay.investmentBudget()).isEqualTo(80_000d);
    assertThat(replay.lines().stream().filter(l -> StrategyHelper.SECURITY_LEVEL_LETTER.equals(l.levelType())))
        .singleElement().satisfies(l -> assertThat(l.targetAmount()).isEqualTo(80_000d));
    assertThat(lineOf(plan(), StrategyHelper.SECURITY_LEVEL_LETTER).targetAmount()).isEqualTo(20_000d);
    assertThat(permitted.getPercentage()).isEqualTo(25f);
  }

  @Test
  @DisplayName("An allocation inside its tolerance proposes nothing")
  void withinToleranceProposesNothing() {
    hierarchy(50f, 100f, 100f);
    snapshot(100_000, 49_000, List.of(position(security, 49, 49_000)));

    RebalancingPlan.Line line = lineOf(plan(), StrategyHelper.SECURITY_LEVEL_LETTER);

    assertThat(line.action()).isEqualTo(AlgoRecommendationAction.REBALANCE_HOLD);
    assertThat(line.rationale()).isEqualTo(AlgoRebalancingService.REASON_WITHIN_TOLERANCE);
    assertThat(line.recommendedAmount()).isNull();
  }

  @Test
  @DisplayName("Too little exposure buys, too much sells, and the units follow the exposure per unit")
  void driftProposesTheDirectionThatClosesIt() {
    hierarchy(50f, 100f, 100f);
    snapshot(100_000, 40_000, List.of(position(security, 40, 40_000)));

    RebalancingPlan.Line under = lineOf(plan(), StrategyHelper.SECURITY_LEVEL_LETTER);
    assertThat(under.action()).isEqualTo(AlgoRecommendationAction.REBALANCE_BUY);
    assertThat(under.recommendedAmount()).isEqualTo(10_000.0);
    assertThat(under.recommendedUnits()).isEqualTo(10.0);
    assertThat(under.rationale()).isEqualTo("REBALANCE_CLASS_INCREASE");

    snapshot(100_000, 60_000, List.of(position(security, 60, 60_000)));
    RebalancingPlan.Line over = lineOf(plan(), StrategyHelper.SECURITY_LEVEL_LETTER);
    assertThat(over.action()).isEqualTo(AlgoRecommendationAction.REBALANCE_SELL);
    assertThat(over.recommendedAmount()).isEqualTo(10_000.0);
    assertThat(over.rationale()).isEqualTo("REBALANCE_CLASS_REDUCTION");
  }

  @Test
  @DisplayName("A short position is measured by its notional and is reduced by buying")
  void shortExposureCountsGrossAndIsReducedByBuying() {
    hierarchy(50f, 100f, 100f);
    // Signed value is negative, gross exposure is not: a short sale does not create investment capacity.
    snapshot(100_000, 60_000, List.of(position(security, -60, -60_000)));

    RebalancingPlan.Line line = lineOf(plan(), StrategyHelper.SECURITY_LEVEL_LETTER);

    assertThat(line.actualPercentage()).isEqualTo(60.0);
    assertThat(line.action()).isEqualTo(AlgoRecommendationAction.REBALANCE_BUY);
    assertThat(line.recommendedAmount()).isEqualTo(10_000.0);
  }

  @Test
  @DisplayName("Exposure beyond the ceiling blocks increases and leaves reductions available")
  void exposureBreachBlocksIncreasesOnly() {
    hierarchy(20f, 100f, 100f);
    Security other = security(41, "Bond ETF", "CHF");
    snapshot(100_000, 45_000, List.of(position(security, 5, 5_000), position(other, 40, 40_000)));

    RebalancingPlan plan = plan();

    assertThat(plan.exposureBreach()).isTrue();
    RebalancingPlan.Line line = lineOf(plan, StrategyHelper.SECURITY_LEVEL_LETTER);
    assertThat(line.action()).isEqualTo(AlgoRecommendationAction.REBALANCE_BLOCKED);
    assertThat(line.rationale()).isEqualTo(AlgoRebalancingService.REASON_EXPOSURE_BREACH);
    assertThat(lineOf(plan, StrategyHelper.TOP_LEVEL_LETTER).action())
        .isEqualTo(AlgoRecommendationAction.REBALANCE_SELL);
  }

  @Test
  @DisplayName("Gross exposure above net equity is reported rather than refused")
  void leveragedBookStillProducesAPlan() {
    hierarchy(90f, 100f, 100f);
    snapshot(50_000, 120_000, List.of(position(security, 120, 120_000)));

    RebalancingPlan plan = plan();

    assertThat(plan.exposureBreach()).isTrue();
    assertThat(plan.grossExposure()).isGreaterThan(plan.netEquity());
    assertThat(lineOf(plan, StrategyHelper.SECURITY_LEVEL_LETTER).action())
        .isEqualTo(AlgoRecommendationAction.REBALANCE_SELL);
  }

  @Test
  @DisplayName("A tactical bucket is a ceiling: it is reduced but never filled")
  void tacticalBucketIsNotFilled() {
    hierarchy(50f, 100f, 100f);
    when(strategies.findByIdAlgoAssetclassSecurityAndIdTenant(ID_MEMBER, ID_TENANT)).thenReturn(
        List.of(strategy(101, AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP, null, null)));
    snapshot(100_000, 10_000, List.of(position(security, 10, 10_000)));

    RebalancingPlan plan = plan();

    RebalancingPlan.Line line = lineOf(plan, StrategyHelper.SECURITY_LEVEL_LETTER);
    assertThat(line.tactical()).isTrue();
    assertThat(line.action()).isEqualTo(AlgoRecommendationAction.REBALANCE_HOLD);
    assertThat(line.rationale()).isEqualTo(AlgoRebalancingService.REASON_TACTICAL_CEILING);
    assertThat(plan.unusedTacticalBudget()).isEqualTo(40_000.0);
  }

  @Test
  @DisplayName("A position in another currency is compared in tenant currency")
  void foreignCurrencyExposureIsConverted() {
    hierarchy(50f, 100f, 100f);
    Security foreign = security(ID_SECURITY, "US ETF", "USD");
    // 500 units worth 100 USD each, at 0.90 CHF per USD, is 45'000 CHF against a 50'000 CHF target.
    Snapshot snapshot = new Snapshot("CHF", List.of(position(foreign, 500, 50_000)), Map.of(),
        Map.of("CHF", 1.0, "USD", 0.9), 100_000, 45_000, List.of());
    when(valuation.value(any(), any(), any())).thenReturn(snapshot);

    RebalancingPlan.Line line = lineOf(plan(), StrategyHelper.SECURITY_LEVEL_LETTER);

    assertThat(line.actualAmount()).isEqualTo(45_000.0);
    assertThat(line.action()).isEqualTo(AlgoRecommendationAction.REBALANCE_BUY);
    assertThat(line.recommendedAmount()).isEqualTo(5_000.0);
    // One unit is 100 USD, that is 90 CHF, so 5'000 CHF buys 55.55 units rather than 50.
    assertThat(line.recommendedUnits()).isCloseTo(55.5555, Offset.offset(1e-4));
  }

  @Test
  @DisplayName("Sibling weights that do not add up to 100% are rejected instead of silently losing budget")
  void incompleteWeightsAreRejected() {
    hierarchy(50f, 80f, 100f);
    snapshot(100_000, 0, List.of());

    assertThatThrownBy(this::plan).isInstanceOf(DataViolationException.class);
  }

  @Test
  @DisplayName("The interval decides whether the allocation is compared, the tolerance which of its lines are traded")
  void intervalGatesTheComparisonAndToleranceTheLines() {
    hierarchy(50f, 100f, 100f);
    snapshot(100_000, 50_000, List.of(position(security, 50, 50_000)));

    // Four redeployments per year is an interval of 91 days; the last plan is one day short of it.
    when(recommendations.findLastCheckpointDate(anyInt(), anyInt()))
        .thenReturn(Optional.of(VALUATION_DATE.minusDays(90)));
    RebalancingPlan notYet = plan();
    assertThat(notYet.periodicDue()).isFalse();
    assertThat(notYet.trigger()).isEqualTo(AlgoRebalancingTrigger.NONE);

    // The checkpoint is reached, but the allocation is on target, so there is still nothing to execute.
    when(recommendations.findLastCheckpointDate(anyInt(), anyInt()))
        .thenReturn(Optional.of(VALUATION_DATE.minusDays(91)));
    RebalancingPlan onTarget = plan();
    assertThat(onTarget.periodicDue()).isTrue();
    assertThat(onTarget.driftDue()).isFalse();
    assertThat(onTarget.executableLines()).isEmpty();
    assertThat(onTarget.trigger()).isEqualTo(AlgoRebalancingTrigger.NONE);

    // A drift on a day between two checkpoints is reported and priced, but it is not an action of its own. This is
    // what made a replay rebalance on every trading day of its run.
    when(recommendations.findLastCheckpointDate(anyInt(), anyInt())).thenReturn(Optional.of(VALUATION_DATE));
    snapshot(100_000, 40_000, List.of(position(security, 40, 40_000)));
    RebalancingPlan drifted = plan();
    assertThat(drifted.periodicDue()).isFalse();
    assertThat(drifted.driftDue()).isTrue();
    assertThat(drifted.executableLines()).hasSize(1);
    assertThat(drifted.trigger()).isEqualTo(AlgoRebalancingTrigger.NONE);

    // The same drift at a checkpoint is what a rebalancing executes.
    when(recommendations.findLastCheckpointDate(anyInt(), anyInt()))
        .thenReturn(Optional.of(VALUATION_DATE.minusDays(91)));
    assertThat(plan().trigger()).isEqualTo(AlgoRebalancingTrigger.PERIODIC);
  }

  @Test
  @DisplayName("Whether a checkpoint is due is answered without valuing the portfolio")
  void checkpointIsAnsweredWithoutAValuation() {
    hierarchy(50f, 100f, 100f);

    when(recommendations.findLastCheckpointDate(anyInt(), anyInt()))
        .thenReturn(Optional.of(VALUATION_DATE.minusDays(90)));
    assertThat(service.isCheckpointDue(ID_TENANT, algoTop, VALUATION_DATE, null)).isFalse();
    assertThat(service.isCheckpointDue(ID_TENANT, algoTop, VALUATION_DATE, VALUATION_DATE.minusDays(91))).isTrue();

    // An environment that has never been rebalanced is due at once, which is how it reaches its targets on day one.
    when(recommendations.findLastCheckpointDate(anyInt(), anyInt())).thenReturn(Optional.empty());
    assertThat(service.isCheckpointDue(ID_TENANT, algoTop, VALUATION_DATE, null)).isTrue();

    verifyNoInteractions(valuation);
  }

  @Test
  @DisplayName("A daily plan between two checkpoints carries the last checkpoint over instead of restarting the interval")
  void persistCarriesTheCheckpointOver() {
    hierarchy(50f, 100f, 100f);
    snapshot(100_000, 40_000, List.of(position(security, 40, 40_000)));

    // Thirty days into a 91-day interval: the plan is stored, but the interval keeps counting from the old checkpoint.
    LocalDate lastCheckpoint = VALUATION_DATE.minusDays(30);
    when(recommendations.findLastCheckpointDate(anyInt(), anyInt())).thenReturn(Optional.of(lastCheckpoint));
    RebalancingPlan between = plan();
    assertThat(between.periodicDue()).isFalse();
    service.persist(between);
    assertThat(storedCheckpoints()).containsOnly(lastCheckpoint);

    // At the checkpoint the valuation day becomes the new start of the interval.
    when(recommendations.findLastCheckpointDate(anyInt(), anyInt()))
        .thenReturn(Optional.of(VALUATION_DATE.minusDays(91)));
    RebalancingPlan atCheckpoint = plan();
    assertThat(atCheckpoint.periodicDue()).isTrue();
    service.persist(atCheckpoint);
    assertThat(storedCheckpoints()).containsOnly(VALUATION_DATE);
  }

  @Test
  @DisplayName("Class drift triggers three purchases even when each security is less than five equity points under target")
  void classDriftSelectsOnlyThreeOfTenSmallPositions() {
    tenPositions();
    var plan = plan();
    assertThat(plan.executableLines()).hasSize(3);
    assertThat(plan.executableLines()).allSatisfy(line -> {
      assertThat(line.deviation()).isEqualTo(-3);
      assertThat(line.recommendedAmount()).isEqualTo(7_000);
      assertThat(line.parentDeviation()).isEqualTo(-3.75);
    });
    var adjustment = plan.classAdjustments().getFirst();
    assertThat(adjustment.requestedAdjustment()).isEqualTo(30_000);
    assertThat(adjustment.plannedAdjustment()).isEqualTo(21_000);
    assertThat(adjustment.residual()).isEqualTo(9_000);
  }

  @Test
  void classOverridesResolveIndependentlyAndZeroDoesNotInherit() {
    tenPositions();
    AlgoAssetclass bucket = bucketNode();
    bucket.setSecurityDeviationPercentage(0.0);
    when(buckets.findByIdTenantAndIdAlgoAssetclassParent(ID_TENANT, ID_ALGO_TOP)).thenReturn(List.of(bucket));
    assertThat(plan().classAdjustments().getFirst().plannedAdjustment()).isEqualTo(9_000);
    bucket.setMaxTradedSecuritiesPerAssetclass(2);
    assertThat(plan().classAdjustments().getFirst().plannedAdjustment()).isEqualTo(6_000);
    bucket.setSecurityDeviationPercentage(null);
    assertThat(plan().classAdjustments().getFirst().plannedAdjustment()).isEqualTo(14_000);
    bucket.setMaxTradedSecuritiesPerAssetclass(null);
    assertThat(plan().classAdjustments().getFirst().plannedAdjustment()).isEqualTo(21_000);
  }

  @Test
  void initialConstructionStillFillsAllExactTargets() {
    tenPositions();
    var initial = service.initialPlan(ID_TENANT, algoTop, VALUATION_DATE, Locale.ROOT, (_, _) -> 1_000.0);
    assertThat(initial.executableLines()).hasSize(10);
    assertThat(initial.executableLines()).allSatisfy(line -> assertThat(line.recommendedAmount()).isEqualTo(3_000));
    assertThat(initial.classAdjustments()).isEmpty();
  }

  @Test
  void swissBondExampleUsesInvestmentBudgetForTriggerBoundary() {
    hierarchy(80, 30.4f, 100);
    rebalancing.getAlgoRuleStrategyParamMap().put("thresholdPercentage", param(5));
    AlgoAssetclass other = bucketNode();
    other.setIdAlgoAssetclassSecurity(21);
    other.setPercentage(69.6f);
    when(buckets.findByIdTenantAndIdAlgoAssetclassParent(ID_TENANT, ID_ALGO_TOP))
        .thenReturn(List.of(bucketNode(), other));
    double target = 800_000 * (double) 30.4f / 100;
    snapshot(1_000_000, target - 40_000, List.of(position(security, 100, target - 40_000)));
    var atBoundary = plan();
    assertThat(lineOf(atBoundary, "A").targetAmount()).isCloseTo(243_200, Offset.offset(0.01));
    assertThat(atBoundary.executableLines()).isEmpty();
    snapshot(1_000_000, target - 40_000.01, List.of(position(security, 100, target - 40_000.01)));
    assertThat(plan().executableLines()).hasSize(1);
  }

  @Test
  void noInternalRedistributionWhenClassIsOnTarget() {
    hierarchy(80, 100, 50);
    var children = List.of(memberNode(50), otherMember(50));
    when(members.findByIdAlgoSecurityParentAndIdTenant(ID_BUCKET, ID_TENANT)).thenReturn(children);
    snapshot(100_000, 80_000, List.of(position(security, 80, 80_000)));
    assertThat(plan().executableLines()).isEmpty();
  }

  private void tenPositions() {
    hierarchy(80, 100, 10);
    rebalancing.getAlgoRuleStrategyParamMap().put("thresholdPercentage", param(5));
    List<AlgoSecurity> children = new ArrayList<>();
    List<Position> positions = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      AlgoSecurity child = memberNode(10);
      child.setIdAlgoAssetclassSecurity(30 + i);
      child.setSecurity(security(40 + i, "Instrument " + i, "CHF"));
      children.add(child);
      positions.add(position(child.getSecurity(), 5, 5_000));
    }
    when(members.findByIdAlgoSecurityParentAndIdTenant(ID_BUCKET, ID_TENANT)).thenReturn(children);
    snapshot(100_000, 50_000, positions);
  }

  @Test
  void liveAndReplayVolumeSelectTheSameInstrumentWithoutSeeingFutureVolume() {
    tenPositions();
    AlgoAssetclass bucket = bucketNode();
    bucket.setMaxTradedSecuritiesPerAssetclass(1);
    when(buckets.findByIdTenantAndIdAlgoAssetclassParent(ID_TENANT, ID_ALGO_TOP)).thenReturn(List.of(bucket));
    var data = mock(grafioschtrader.repository.AlgoTradingRepository.class);
    for (int id = 40; id < 50; id++) {
      var current = new grafioschtrader.entities.Historyquote(id, VALUATION_DATE, 1_000);
      current.setVolume(id == 44 ? 100L : 1L);
      var future = new grafioschtrader.entities.Historyquote(id, VALUATION_DATE.plusDays(1), 1_000);
      future.setVolume(id == 45 ? 1_000_000L : 1L);
      when(quotes.findFirstByIdSecuritycurrencyAndDateLessThanEqualOrderByDateDesc(id, VALUATION_DATE))
          .thenReturn(Optional.of(current));
      when(data.history(org.mockito.ArgumentMatchers.eq(id), any(), any())).thenReturn(List.of(future, current));
    }
    var market = new AlgoReplayMarketData(data, VALUATION_DATE, VALUATION_DATE.plusDays(1));
    var live = plan();
    var replay = service.plan(ID_TENANT, algoTop, VALUATION_DATE, Locale.ROOT, null, market);
    assertThat(live.executableLines()).extracting(RebalancingPlan.Line::idSecuritycurrency).containsExactly(44);
    assertThat(replay.executableLines()).isEqualTo(live.executableLines());
    assertThat(replay.classAdjustments()).isEqualTo(live.classAdjustments());
  }

  @Test
  void snapshotRecordsEffectiveOverridesAndWeights() {
    tenPositions();
    var bucket = bucketNode();
    bucket.setSecurityDeviationPercentage(0.0);
    when(buckets.findByIdTenantAndIdAlgoAssetclassParent(ID_TENANT, ID_ALGO_TOP)).thenReturn(List.of(bucket));
    var snapshot = new com.fasterxml.jackson.databind.ObjectMapper()
        .valueToTree(service.configurationSnapshot(algoTop));
    assertThat(snapshot.path("version").asText()).isEqualTo(AlgoClassRebalancingAllocator.VERSION);
    var stored = snapshot.path("classes").get(0);
    assertThat(stored.path("securityDeviationPercentage").asDouble()).isZero();
    assertThat(stored.path("maxTradedSecuritiesPerAssetclass").asInt()).isEqualTo(3);
    assertThat(stored.path("securities").size()).isEqualTo(10);
  }

  // -----------------------------------------------------------------------------------------------------------------

  /** The checkpoint days of the rows most recently handed to the writer. */
  @SuppressWarnings("unchecked")
  private List<LocalDate> storedCheckpoints() {
    ArgumentCaptor<List<AlgoRecommendation>> rows = ArgumentCaptor.forClass(List.class);
    verify(writer, atLeastOnce()).replace(anyInt(), anyInt(), rows.capture());
    return rows.getValue().stream().map(AlgoRecommendation::getCheckpointDate).toList();
  }

  private RebalancingPlan plan() {
    return service.plan(ID_TENANT, algoTop, VALUATION_DATE, Locale.ROOT);
  }

  private RebalancingPlan.Line lineOf(RebalancingPlan plan, String levelType) {
    return plan.lines().stream().filter(line -> levelType.equals(line.levelType())).findFirst().orElseThrow();
  }

  /** One bucket with one instrument, so that the three weights of a plan are the three arguments. */
  private void hierarchy(float top, float bucket, float member) {
    algoTop = algoTop(top);
    bucketPercentage = bucket;
    when(algoTops.findByIdTenantAndIdAlgoAssetclassSecurity(ID_TENANT, ID_ALGO_TOP)).thenReturn(algoTop);
    when(buckets.findByIdTenantAndIdAlgoAssetclassParent(ID_TENANT, ID_ALGO_TOP)).thenReturn(List.of(bucketNode()));
    when(members.findByIdAlgoSecurityParentAndIdTenant(ID_BUCKET, ID_TENANT)).thenReturn(List.of(memberNode(member)));
  }

  private AlgoAssetclass bucketNode() {
    AlgoAssetclass assetclass = new AlgoAssetclass();
    assetclass.setIdAlgoAssetclassSecurity(ID_BUCKET);
    assetclass.setIdTenant(ID_TENANT);
    assetclass.setPercentage(bucketPercentage);
    assetclass.setName("Strategic equities");
    return assetclass;
  }

  private AlgoSecurity memberNode(float percentage) {
    AlgoSecurity algoSecurity = new AlgoSecurity();
    algoSecurity.setIdAlgoAssetclassSecurity(ID_MEMBER);
    algoSecurity.setIdTenant(ID_TENANT);
    algoSecurity.setPercentage(percentage);
    algoSecurity.setSecurity(security);
    return algoSecurity;
  }

  private AlgoSecurity otherMember(float percentage) {
    AlgoSecurity algoSecurity = new AlgoSecurity();
    algoSecurity.setIdAlgoAssetclassSecurity(31);
    algoSecurity.setIdTenant(ID_TENANT);
    algoSecurity.setPercentage(percentage);
    algoSecurity.setSecurity(security(41, "US ETF", "CHF"));
    return algoSecurity;
  }

  private void snapshot(double equity, double grossExposure, List<Position> positions) {
    when(valuation.value(any(), any(), any())).thenReturn(
        new Snapshot("CHF", positions, Map.of(), Map.of("CHF", 1.0), equity, grossExposure, new ArrayList<>()));
  }

  private static Position position(Security security, double units, double closingValue) {
    return new Position("key" + security.getIdSecuritycurrency(), security, 1, units, closingValue,
        Math.abs(closingValue), null);
  }

  private static AlgoTop algoTop(float percentage) {
    AlgoTop algoTop = new AlgoTop();
    algoTop.setIdAlgoAssetclassSecurity(ID_ALGO_TOP);
    algoTop.setIdTenant(ID_TENANT);
    algoTop.setName("Balanced");
    algoTop.setPercentage(percentage);
    algoTop.setActivatable(true);
    return algoTop;
  }

  private static AlgoStrategy strategy(int id, AlgoStrategyImplementationType type, Integer timePeriodPerYear,
      Integer thresholdPercentage) {
    AlgoStrategy strategy = new AlgoStrategy();
    strategy.setIdAlgoRuleStrategy(id);
    strategy.setIdTenant(ID_TENANT);
    strategy.setAlgoStrategyImplementations(type);
    strategy.setActivatable(true);
    if (timePeriodPerYear != null) {
      strategy.getAlgoRuleStrategyParamMap().put("timePeriodPerYear", param(timePeriodPerYear));
      strategy.getAlgoRuleStrategyParamMap().put("thresholdPercentage", param(thresholdPercentage));
    }
    return strategy;
  }

  private static AlgoRuleStrategyParam param(Integer value) {
    AlgoRuleStrategyParam param = new AlgoRuleStrategyParam();
    param.setParamValue(String.valueOf(value));
    return param;
  }

  private static Security security(int id, String name, String currency) {
    Security security = mock(Security.class);
    when(security.getId()).thenReturn(id);
    when(security.getIdSecuritycurrency()).thenReturn(id);
    when(security.getName()).thenReturn(name);
    when(security.getCurrency()).thenReturn(currency);
    return security;
  }

}
