package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.Test;

import grafioschtrader.algo.strategy.model.complex.*;
import grafioschtrader.algo.strategy.model.complex.downside.*;
import grafioschtrader.algo.strategy.model.complex.enums.*;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.service.AlgoMeanReversionDecisionService.*;

/** Hand-calculated averaging-down decisions, including composition with actual profit taking fills. */
class AlgoAverageDownModuleTest {
  private final LocalDate day = LocalDate.of(2026, 9, 7);
  private final AlgoMeanReversionDecisionService engine = new AlgoMeanReversionDecisionService(
      new AlgoScaleOutModule());

  private StrategyConfig config() throws Exception {
    return AlgoMeanReversionDecisionServiceTest.config("/testdata/average-down-strategy.json");
  }

  private Position position(int direction, int adds) {
    return new Position(10, direction * 10, 10, 0, 100, 100, day.minusDays(3), null, 1, adds, List.of());
  }

  private Context context(double price, Position position, double capacity) {
    return new Context(1, 2, 3, day,
        (_, _) -> List.of(new Historyquote(1, day.minusDays(1), 100), new Historyquote(2, day, price)), position,
        100000, capacity, capacity, capacity, Math.abs(position.signedUnits()) * price, price, true);
  }

  @Test
  void initialEntryLadderAndShortSymmetry() throws Exception {
    var c = config();
    for (int direction : new int[] { 1, -1 }) {
      for (int adds = 0; adds < 3; adds++) {
        double price = 100 * (1 - direction * (0.10 + adds * 0.05));
        var d = engine.evaluate(c, context(price, position(direction, adds), 10000));
        assertEquals(Action.ADD, d.action());
        assertEquals(1000 / price, d.quantity(), 1e-8);
        assertEquals(direction, d.direction());
        assertEquals(Action.HOLD,
            engine.evaluate(c, context(price + direction * 0.01, position(direction, adds), 10000)).action());
      }
    }
    assertEquals(Action.BLOCKED, engine.evaluate(c, context(79, position(1, 3), 10000)).action());
  }

  @Test
  void averageReferenceUsesCurrentCostAndEquitySizing() throws Exception {
    var c = config();
    var a = c.downside_management.variant_B_average_down;
    a.add_step_rule.reference = ReferencePrice.avg_cost;
    a.add_sizing.mode = SizingMode.pct_portfolio;
    a.add_sizing.pct = 0.02;
    var p = new Position(10, 20, 10, 0, 100, 90, day.minusDays(3), null, 2, 1, List.of());
    assertEquals(Action.HOLD, engine.evaluate(c, context(86, p, 10000)).action());
    assertEquals(2000 / 85.5, engine.evaluate(c, context(85.5, p, 10000)).quantity(), 1e-8);
  }

  @Test
  void budgetsCooldownsAndRiskBlockWithoutLiquidation() throws Exception {
    var c = config();
    var p = position(1, 0);
    assertEquals(Action.BLOCKED, engine.evaluate(c, context(90, p, 999)).action());
    var today = new Position(10, 10, 10, 0, 100, 100, day, null, 1, 0, List.of());
    assertEquals(Action.BLOCKED, engine.evaluate(c, context(90, today, 10000)).action());
    c.risk_controls.force_exit_on_risk_breach = false;
    assertEquals("AVERAGE_DOWN_RISK_BLOCK", engine.evaluate(c, context(74, p, 10000)).rationale());
    c.risk_controls.force_exit_on_risk_breach = true;
    assertEquals(Action.RISK_EXIT, engine.evaluate(c, context(74, p, 10000)).action());
    var base = context(90, p, 1500);
    var reserved = new Context(1, 2, 3, day, base.market(), p, 100000, 1500, 1500, 1500, 900, 90, true, 600, 600, 600);
    assertEquals("MEAN_REVERSION_BUDGET_EXHAUSTED", engine.evaluate(c, reserved).rationale());
  }

  @Test
  void indicatorsConfirmButNeverReplaceTheAdverseMove() throws Exception {
    var c = config();
    var trigger = c.downside_management.trigger;
    trigger.decision_basis = DecisionBasis.indicator;
    var rule = new IndicatorRuleConfig();
    rule.type = IndicatorType.sma;
    rule.params = new IndicatorParams();
    rule.params.length = 1;
    rule.params.condition = "<=";
    rule.params.value = 100.0;
    trigger.indicator_rules = List.of(rule);
    assertEquals(Action.HOLD, engine.evaluate(c, context(95, position(1, 0), 10000)).action());
    assertEquals(Action.ADD, engine.evaluate(c, context(90, position(1, 0), 10000)).action());
    rule.params.length = 100;
    assertEquals(Action.UNAVAILABLE, engine.evaluate(c, context(90, position(1, 0), 10000)).action());
  }

  @Test
  void executableValidationRejectsAmbiguousOrUndefinedSettings() throws Exception {
    var c = config();
    c.downside_management.variant_B_average_down.recalculate_avg_cost = false;
    assertThrows(IllegalArgumentException.class, () -> MeanReversionConfigValidator.validate(c));
    c.downside_management.variant_B_average_down.recalculate_avg_cost = true;
    c.downside_management.variant_B_average_down.add_step_rule.type = AddStepType.custom;
    assertThrows(IllegalArgumentException.class, () -> MeanReversionConfigValidator.validate(c));
    c.downside_management.variant_B_average_down.add_step_rule.type = AddStepType.indicator_based;
    assertThrows(IllegalArgumentException.class, () -> MeanReversionConfigValidator.validate(c));
  }

  @Test
  void currentPositionTranchesFreezeFromFirstFillAndDoNotRepeatAfterAdds() throws Exception {
    var c = config();
    c.profit_management.sell_fraction_basis = SellFractionBasis.current_position;
    var fills = List.of(new PositionFill(10, Map.of()), new PositionFill(10, Map.of()));
    var p = new Position(10, 20, 10, 0, 100, 90, day.minusDays(3), null, 2, 1, fills);
    var first = engine.evaluate(c, context(93, p, 10000));
    assertEquals(Action.SCALE_OUT, first.action());
    assertEquals(6, first.quantity(), 1e-8);
    var partial = new ArrayList<>(fills);
    partial.add(new PositionFill(-2, first.trancheTargets()));
    partial.add(new PositionFill(10, Map.of()));
    p = new Position(10, 28, 10, 2, 100, 90, day.minusDays(3), day.minusDays(2), 4, 2, partial);
    assertEquals(4, engine.evaluate(c, context(93, p, 10000)).quantity(), 1e-8);
    partial.add(new PositionFill(-4, first.trancheTargets()));
    p = new Position(10, 24, 10, 6, 100, 90, day.minusDays(3), day.minusDays(2), 5, 2, partial);
    assertEquals(Action.HOLD, engine.evaluate(c, context(93, p, 10000)).action());
    assertEquals(7.2, engine.evaluate(c, context(95, p, 10000)).quantity(), 1e-8);
  }

  @Test
  void profitTakingPrecedesAdditionEvenWhenReferencesDiffer() throws Exception {
    var c = config();
    var p = new Position(10, 20, 10, 0, 100, 80, day.minusDays(3), null, 2, 1,
        List.of(new PositionFill(10, Map.of()), new PositionFill(10, Map.of())));
    assertEquals(Action.SCALE_OUT, engine.evaluate(c, context(85, p, 10000)).action());
  }
}
