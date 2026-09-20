package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.Test;

import grafioschtrader.algo.strategy.model.complex.*;
import grafioschtrader.algo.strategy.model.complex.enums.*;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.service.AlgoMeanReversionDecisionService.*;

/** Deterministic calculation tests with no Spring context, database or network. */
class AlgoMeanReversionDecisionServiceTest {
  private final AlgoMeanReversionDecisionService engine = new AlgoMeanReversionDecisionService(
      new AlgoScaleOutModule());
  private final LocalDate day = LocalDate.of(2026, 9, 7);

  static StrategyConfig config() throws Exception {
    return config("/testdata/mean-reversion-strategy.json");
  }

  static StrategyConfig config(String fixture) throws Exception {
    try (var in = AlgoMeanReversionDecisionServiceTest.class.getResourceAsStream(fixture)) {
      return StrategyConfigValidator.executable(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  Context context(double price, Position p, double capacity) {
    List<Historyquote> history = List.of(new Historyquote(1, day.minusDays(2), 100),
        new Historyquote(2, day.minusDays(1), 100), new Historyquote(3, day, price));
    return new Context(1, 2, 3, day, (_, _) -> history, p, 100000, capacity, capacity, capacity,
        Math.abs(p.signedUnits()) * price, price, true);
  }

  @Test
  void mirroredEntrySizingAndExhaustion() throws Exception {
    var c = config();
    var buy = engine.evaluate(c, context(89, Position.empty(), 10000));
    assertEquals(Action.ENTRY, buy.action());
    assertEquals(3000 / 89.0, buy.quantity(), 1e-10);
    c.universe.direction = Direction.short_only;
    var sell = engine.evaluate(c, context(111, Position.empty(), 10000));
    assertEquals(Action.ENTRY, sell.action());
    assertEquals(-1, sell.direction());
    assertEquals(Action.BLOCKED, engine.evaluate(c, context(111, Position.empty(), 2999)).action());
    c.entry.initial_buy_sizing.mode = SizingMode.absolute_amount;
    c.entry.initial_buy_sizing.amount = 2220.0;
    assertEquals(20, engine.evaluate(c, context(111, Position.empty(), 10000)).quantity(), 1e-10);
  }

  @Test
  void longAndShortFullExitsIgnoreEntryCooldown() throws Exception {
    for (int dir : new int[] { 1, -1 }) {
      var p = new Position(10, dir * 10, 10, 0, 100, 100, day, null, 10);
      var stop = engine.evaluate(config(), context(dir == 1 ? 89 : 111, p, 10000));
      assertEquals(Action.STOP_EXIT, stop.action());
      assertEquals(10, stop.quantity());
      var profit = engine.evaluate(config(), context(dir == 1 ? 111 : 89, p, 10000));
      assertEquals(Action.TAKE_PROFIT_EXIT, profit.action());
      var risk = engine.evaluate(config(), context(100, p, -1));
      assertEquals(Action.RISK_EXIT, risk.action());
    }
  }

  @Test
  void cooldownReopeningAndIdentity() throws Exception {
    var yesterday = new Position(10, 0, 0, 0, 100, 100, day.minusDays(5), day.minusDays(1), 2);
    assertEquals(Action.BLOCKED, engine.evaluate(config(), context(89, yesterday, 10000)).action());
    var eligible = new Position(10, 0, 0, 0, 100, 100, day.minusDays(5), day.minusDays(2), 2);
    var first = engine.evaluate(config(), context(89, eligible, 10000));
    assertEquals(Action.ENTRY, first.action());
    assertEquals(first.identity(), engine.evaluate(config(), context(89, eligible, 10000)).identity());
  }

  @Test
  void missingFutureAndUnassignedInputsNeverTrade() throws Exception {
    var base = context(89, Position.empty(), 10000);
    for (var history : List.of(List.<Historyquote>of(), List.of(new Historyquote(1, day.plusDays(1), 89)))) {
      var c = new Context(1, 2, 3, day, (_, _) -> history, Position.empty(), 100000, 10000, 10000, 10000, 0, 89, true);
      assertEquals(Action.UNAVAILABLE, engine.evaluate(config(), c).action());
    }
    var c = new Context(1, 2, 3, day, base.market(), Position.empty(), 100000, 10000, 10000, 10000, 0, 89, false);
    assertEquals(Action.UNAVAILABLE, engine.evaluate(config(), c).action());
  }

  @Test
  void windowAndMovingAverageReferences() throws Exception {
    var c = config();
    c.entry.dip_reference.type = DipReferenceType.highest_in_window;
    c.entry.dip_reference.period = 2;
    assertEquals(Action.ENTRY, engine.evaluate(c, context(89, Position.empty(), 10000)).action());
    c.entry.dip_reference.type = DipReferenceType.moving_average;
    c.entry.dip_reference.indicator = "SMA";
    c.entry.dip_reference.period = 1;
    assertEquals(Action.ENTRY, engine.evaluate(c, context(89, Position.empty(), 10000)).action());
  }

  @Test
  void executableValidationRejectsLaterModulesAndUnknownFields() throws Exception {
    var c = config();
    c.downside_management.variant_B_average_down.enabled = true;
    assertThrows(IllegalArgumentException.class, () -> MeanReversionConfigValidator.validate(c));
    c.downside_management.variant_B_average_down.enabled = false;
    c.downside_management.loss_action = LossAction.B_average_down;
    assertThrows(IllegalArgumentException.class, () -> MeanReversionConfigValidator.validate(c));
    c.downside_management.loss_action = LossAction.A_sell_loss;
    c.universe.assets = List.of("AAPL");
    assertThrows(IllegalArgumentException.class, () -> MeanReversionConfigValidator.validate(c));
    var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
    String json = mapper.writeValueAsString(config()).replace("\"strategy_name\"", "\"misspelled_name\"");
    assertThrows(Exception.class, () -> StrategyConfigValidator.executable(json));
  }

  @Test
  void aTriggeredTrancheYieldsToEveryFullExitAndNamesItselfInTheIdentity() throws Exception {
    var c = config("/testdata/scale-out-strategy.json");
    var p = new Position(10, 10, 10, 0, 100, 100, day, null, 10);
    var scale = engine.evaluate(c, context(103, p, 10000));
    assertEquals(Action.SCALE_OUT, scale.action());
    assertEquals("SCALE_OUT", scale.rationale());
    assertEquals("t1", scale.tranche());
    assertEquals(3, scale.quantity(), 1e-10);
    assertTrue(scale.actionable());
    assertTrue(scale.identity().endsWith(":SCALE_OUT:1:t1"), scale.identity());
    // The same price with the plan already settled proposes nothing more, however often it is evaluated.
    assertEquals(Action.HOLD,
        engine.evaluate(c, context(103, new Position(10, 7, 10, 3, 100, 100, day, null, 10), 10000)).action());
    // A full exit is the more risk reducing action, so it wins wherever both would fire.
    assertEquals(Action.RISK_EXIT, engine.evaluate(c, context(103, p, -1)).action());
    assertEquals(Action.TAKE_PROFIT_EXIT, engine.evaluate(c, context(150, p, 10000)).action());
    // A strategy without a plan never produces a partial exit, and its identity is the one it always had.
    var withoutPlan = engine.evaluate(config(), context(103, p, 10000));
    assertEquals(Action.HOLD, withoutPlan.action());
    assertEquals("", withoutPlan.tranche());
    assertTrue(withoutPlan.identity().endsWith(":HOLD:1"), withoutPlan.identity());
  }

  @Test
  void thresholdEqualityIsInclusiveDespiteBinaryRounding() throws Exception {
    assertEquals(Action.ENTRY, engine.evaluate(config(), context(90, Position.empty(), 10000)).action());
    assertEquals(Action.STOP_EXIT,
        engine.evaluate(config(), context(90, new Position(1, 10, 10, 0, 100, 100, null, null, 1), 10000)).action());
  }

  @Test
  void statisticalAndHybridRulesRequireRealVariation() throws Exception {
    var c = config();
    var rule = new grafioschtrader.algo.strategy.model.complex.downside.StatisticalRuleConfig();
    rule.type = "zscore";
    rule.params = new grafioschtrader.algo.strategy.model.complex.downside.IndicatorParams();
    rule.params.lookback = 3;
    rule.params.condition = "<=";
    rule.params.value = -1.0;
    c.downside_management.trigger.decision_basis = DecisionBasis.statistical;
    c.downside_management.trigger.statistical_rules = List.of(rule);
    c.downside_management.variant_A_sell_loss.stop_threshold_pct = -.5;
    var p = new Position(1, 10, 10, 0, 100, 100, null, null, 1);
    assertEquals(Action.STOP_EXIT, engine.evaluate(c, context(95, p, 10000)).action());
    assertEquals("MEAN_REVERSION_ZERO_VARIANCE", engine.evaluate(c, context(100, p, 10000)).rationale());
    var indicator = new grafioschtrader.algo.strategy.model.complex.downside.IndicatorRuleConfig();
    indicator.type = IndicatorType.sma;
    indicator.params = new grafioschtrader.algo.strategy.model.complex.downside.IndicatorParams();
    indicator.params.length = 2;
    indicator.params.condition = "<";
    indicator.params.value = 100.0;
    c.downside_management.trigger.indicator_rules = List.of(indicator);
    c.downside_management.trigger.decision_basis = DecisionBasis.hybrid;
    assertEquals(Action.HOLD, engine.evaluate(c, context(95, p, 10000)).action());
    assertEquals(Action.STOP_EXIT, engine.evaluate(c, context(89, p, 10000)).action());
  }
}
