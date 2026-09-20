package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.algo.strategy.model.complex.ProfitManagementValidator;
import grafioschtrader.algo.strategy.model.complex.StrategyConfigValidator;
import grafioschtrader.algo.strategy.model.complex.downside.IndicatorParams;
import grafioschtrader.algo.strategy.model.complex.downside.IndicatorRuleConfig;
import grafioschtrader.algo.strategy.model.complex.enums.IndicatorType;
import grafioschtrader.algo.strategy.model.complex.enums.ReferencePrice;
import grafioschtrader.algo.strategy.model.complex.enums.SellFractionBasis;
import grafioschtrader.algo.strategy.model.complex.enums.TriggerType;
import grafioschtrader.algo.strategy.model.complex.profit.ProfitManagementConfig;
import grafioschtrader.algo.strategy.model.complex.profit.ScaleOutTrancheConfig;
import grafioschtrader.algo.strategy.model.complex.profit.TriggerConfig;
import grafioschtrader.service.AlgoMeanReversionDecisionService.Position;
import grafioschtrader.service.AlgoScaleOutModule.ScaleOut;

/**
 * Deterministic calculation tests of the profit taking module, with no Spring context, database or network.
 *
 * <p>
 * Every configuration here is built in code rather than parsed from a strategy: the module is meant to be reusable by
 * any strategy, and a test that cannot exercise it without an entry rule would not show that.
 * </p>
 */
class AlgoScaleOutModuleTest {

  private final AlgoScaleOutModule module = new AlgoScaleOutModule();

  /** No indicator is asked for unless a trigger is an indicator trigger. */
  private static final AlgoScaleOutModule.Indicators NO_INDICATORS = (_, _) -> {
    throw new AssertionError("An indicator was requested although no trigger asks for one");
  };

  private static TriggerConfig gain(double pct) {
    TriggerConfig trigger = new TriggerConfig();
    trigger.type = TriggerType.pct_gain;
    trigger.value = pct;
    trigger.reference = ReferencePrice.avg_cost;
    return trigger;
  }

  private static ScaleOutTrancheConfig tranche(String id, Double fraction, TriggerConfig trigger) {
    ScaleOutTrancheConfig t = new ScaleOutTrancheConfig();
    t.id = id;
    t.trigger = trigger;
    if (fraction == null)
      t.sell_remainder = true;
    else
      t.sell_fraction = fraction;
    return t;
  }

  /** Three tranches at 3%, 5% and 8%, the last one closing whatever is left. */
  private static ProfitManagementConfig plan(SellFractionBasis basis) {
    ProfitManagementConfig config = new ProfitManagementConfig();
    config.scale_out_enabled = true;
    config.sell_fraction_basis = basis;
    config.scale_out_plan = new ArrayList<>(
        List.of(tranche("t1", 0.3, gain(0.03)), tranche("t2", 0.3, gain(0.05)), tranche("t3", null, gain(0.08))));
    return config;
  }

  /** A long position of 100 units opened at 100, with the given part of it already given back. */
  private static Position position(double open, double realized) {
    return new Position(1, open, 100, realized, 100, 100, null, null, 0);
  }

  private ScaleOut fired(ProfitManagementConfig config, Position position, double price) {
    return fired(config, position, price, 1);
  }

  private ScaleOut fired(ProfitManagementConfig config, Position position, double price, int direction) {
    return module.evaluate(config, position, price, direction, price, NO_INDICATORS)
        .orElseThrow(() -> new AssertionError("Expected a tranche to fire at " + price));
  }

  private void quiet(ProfitManagementConfig config, Position position, double price) {
    assertTrue(module.evaluate(config, position, price, 1, price, NO_INDICATORS).isEmpty(),
        "Expected no tranche at " + price);
  }

  @Test
  void initialBasisMeasuresEveryFractionAgainstTheOpeningSize() {
    var config = plan(SellFractionBasis.initial_position);
    var open = position(100, 0);
    quiet(config, open, 102);
    var first = fired(config, open, 103);
    assertEquals("t1", first.tranche());
    assertEquals(30, first.quantity(), 1e-10);
    assertFalse(first.remainder());
  }

  @Test
  void currentBasisMeasuresEveryFractionAgainstWhatIsLeft() {
    var config = plan(SellFractionBasis.current_position);
    var open = position(100, 0);
    assertEquals(30, fired(config, open, 103).quantity(), 1e-10);
    // 30 units go with the first tranche, then 30% of the remaining 70, so 51 units in total.
    assertEquals(51, fired(config, open, 105).quantity(), 1e-10);
  }

  @Test
  void oneMoveMaySettleSeveralTranchesButNeverSkipsOne() {
    var config = plan(SellFractionBasis.initial_position);
    var second = fired(config, position(100, 0), 105);
    assertEquals("t2", second.tranche());
    assertEquals(60, second.quantity(), 1e-10);
    var final_ = fired(config, position(100, 0), 108);
    assertEquals("t3", final_.tranche());
    assertTrue(final_.remainder());
    assertEquals(100, final_.quantity(), 1e-10);
  }

  @Test
  void aTrancheAlreadyGivenBackNeverFiresAgain() {
    var config = plan(SellFractionBasis.initial_position);
    var afterFirst = position(70, 30);
    quiet(config, afterFirst, 103);
    quiet(config, afterFirst, 104);
    var second = fired(config, afterFirst, 105);
    assertEquals("t2", second.tranche());
    assertEquals(30, second.quantity(), 1e-10);
  }

  @Test
  void aPartialFillReproposesOnlyItsRemainder() {
    var config = plan(SellFractionBasis.initial_position);
    var partly = fired(config, position(90, 10), 103);
    assertEquals("t1", partly.tranche());
    assertEquals(20, partly.quantity(), 1e-10);
    // Nothing at all was filled, so the whole tranche is proposed again unchanged.
    assertEquals(30, fired(config, position(100, 0), 103).quantity(), 1e-10);
  }

  @Test
  void noCombinationCanCloseMoreThanIsOpen() {
    var config = plan(SellFractionBasis.initial_position);
    // A reduction booked outside the plan leaves less than the plan would give back.
    assertEquals(10, fired(config, position(10, 0), 105).quantity(), 1e-10);
    assertEquals(5, fired(config, position(5, 0), 108).quantity(), 1e-10);
    assertTrue(module.evaluate(config, position(0, 100), 108, 1, 108, NO_INDICATORS).isEmpty());
  }

  @Test
  void shortPositionsCoverOnTheMirroredMove() {
    var config = plan(SellFractionBasis.initial_position);
    var shortPosition = new Position(1, -100, 100, 0, 100, 100, null, null, 0);
    assertTrue(module.evaluate(config, shortPosition, 103, -1, 103, NO_INDICATORS).isEmpty());
    var covered = fired(config, shortPosition, 97, -1);
    assertEquals("t1", covered.tranche());
    assertEquals(30, covered.quantity(), 1e-10);
  }

  @Test
  void absoluteProfitIsMeasuredInTenantCurrency() {
    var config = plan(SellFractionBasis.initial_position);
    var trigger = config.scale_out_plan.getFirst().trigger;
    trigger.type = TriggerType.absolute_profit;
    trigger.value = 250.0;
    config.scale_out_plan = List.of(config.scale_out_plan.getFirst());
    // Three percent on a hundred units bought at a hundred is three hundred, in a tenant currency of the instrument.
    assertEquals(30, fired(config, position(100, 0), 103).quantity(), 1e-10);
    trigger.value = 350.0;
    quiet(config, position(100, 0), 103);
  }

  @Test
  void anIndicatorTriggerFiresOnlyWhenEveryRuleHolds() {
    var config = plan(SellFractionBasis.initial_position);
    var trigger = new TriggerConfig();
    trigger.type = TriggerType.indicator;
    trigger.indicator_rules = List.of(rule(IndicatorType.rsi, 14, ">", 70.0), rule(IndicatorType.sma, 5, "<", 120.0));
    config.scale_out_plan = List.of(tranche("t1", 0.25, trigger));
    var position = position(100, 0);
    AlgoScaleOutModule.Indicators overbought = (type, _) -> "rsi".equals(type) ? 75 : 110;
    assertEquals(25, module.evaluate(config, position, 100, 1, 100, overbought).orElseThrow().quantity(), 1e-10);
    AlgoScaleOutModule.Indicators calm = (type, _) -> "rsi".equals(type) ? 55 : 110;
    assertTrue(module.evaluate(config, position, 100, 1, 100, calm).isEmpty());
  }

  @Test
  void nothingHappensWithoutAnEnabledPlanOrAnOpenPosition() {
    var config = plan(SellFractionBasis.initial_position);
    config.scale_out_enabled = false;
    quiet(config, position(100, 0), 108);
    config.scale_out_enabled = true;
    config.scale_out_plan = List.of();
    quiet(config, position(100, 0), 108);
    quiet(plan(SellFractionBasis.initial_position), position(0, 0), 108);
    assertTrue(module.evaluate(null, position(100, 0), 108, 1, 108, NO_INDICATORS).isEmpty());
  }

  @Test
  void aLifecycleWithoutARecordedOpeningSizeFallsBackToWhatItAccountsFor() {
    var config = plan(SellFractionBasis.initial_position);
    var legacy = new Position(1, 80, 0, 20, 100, 100, null, null, 0);
    assertEquals(10, fired(config, legacy, 103).quantity(), 1e-10);
  }

  @Test
  void theFixtureIsExecutableAndPlansTheEngineCannotRunAreRejected() throws Exception {
    try (var in = AlgoScaleOutModuleTest.class.getResourceAsStream("/testdata/scale-out-strategy.json")) {
      var config = StrategyConfigValidator.executable(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      assertEquals("t1", fired(config.profit_management, position(100, 0), 103).tranche());
    }
    var config = plan(SellFractionBasis.initial_position);
    ProfitManagementValidator.validate(config);

    var noPlan = plan(SellFractionBasis.initial_position);
    noPlan.scale_out_plan = List.of();
    assertThrows(IllegalArgumentException.class, () -> ProfitManagementValidator.validate(noPlan));

    var noBasis = plan(SellFractionBasis.initial_position);
    noBasis.sell_fraction_basis = null;
    assertThrows(IllegalArgumentException.class, () -> ProfitManagementValidator.validate(noBasis));

    var duplicate = plan(SellFractionBasis.initial_position);
    duplicate.scale_out_plan.get(1).id = "t1";
    assertThrows(IllegalArgumentException.class, () -> ProfitManagementValidator.validate(duplicate));

    var earlyRemainder = plan(SellFractionBasis.initial_position);
    earlyRemainder.scale_out_plan.getFirst().sell_fraction = null;
    earlyRemainder.scale_out_plan.getFirst().sell_remainder = true;
    assertThrows(IllegalArgumentException.class, () -> ProfitManagementValidator.validate(earlyRemainder));

    var both = plan(SellFractionBasis.initial_position);
    both.scale_out_plan.getFirst().sell_remainder = true;
    assertThrows(IllegalArgumentException.class, () -> ProfitManagementValidator.validate(both));

    var overSold = plan(SellFractionBasis.initial_position);
    overSold.scale_out_plan.get(1).sell_fraction = 0.8;
    assertThrows(IllegalArgumentException.class, () -> ProfitManagementValidator.validate(overSold));
    // The same fractions are fine when each one is measured against what is left at the time.
    overSold.sell_fraction_basis = SellFractionBasis.current_position;
    ProfitManagementValidator.validate(overSold);

    var strayRules = plan(SellFractionBasis.initial_position);
    strayRules.scale_out_plan.getFirst().trigger.indicator_rules = List.of(rule(IndicatorType.rsi, 14, ">", 70.0));
    assertThrows(IllegalArgumentException.class, () -> ProfitManagementValidator.validate(strayRules));

    var ruleless = plan(SellFractionBasis.initial_position);
    ruleless.scale_out_plan.getFirst().trigger.type = TriggerType.indicator;
    assertThrows(IllegalArgumentException.class, () -> ProfitManagementValidator.validate(ruleless));

    var badId = plan(SellFractionBasis.initial_position);
    badId.scale_out_plan.getFirst().id = "t 1";
    assertThrows(IllegalArgumentException.class, () -> ProfitManagementValidator.validate(badId));
  }

  private static IndicatorRuleConfig rule(IndicatorType type, int length, String condition, double value) {
    IndicatorRuleConfig rule = new IndicatorRuleConfig();
    rule.type = type;
    rule.params = new IndicatorParams();
    rule.params.length = length;
    rule.params.condition = condition;
    rule.params.value = value;
    return rule;
  }
}
