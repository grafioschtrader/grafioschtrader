package grafioschtrader.service;

import java.time.LocalDate;
import java.util.*;

import org.springframework.stereotype.Service;

import grafioschtrader.algo.strategy.model.complex.*;
import grafioschtrader.algo.strategy.model.complex.downside.IndicatorParams;
import grafioschtrader.algo.strategy.model.complex.enums.*;
import grafioschtrader.entities.AlgoMessageAlert;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.evalex.AlertExpressionSupport;

/** Pure daily-close decisions. Neither quotes, the wall clock nor a transaction repository can be accessed here. */
@Service
public class AlgoMeanReversionDecisionService {
  private final AlgoScaleOutModule scaleOut;

  public AlgoMeanReversionDecisionService(AlgoScaleOutModule scaleOut) {
    this.scaleOut = scaleOut;
  }

  public enum Action {
    ENTRY, ADD, SCALE_OUT, STOP_EXIT, TAKE_PROFIT_EXIT, RISK_EXIT, HOLD, BLOCKED, UNAVAILABLE
  }

  /** Implementations must supply ordered, finite, positive observations through the requested date only. */
  public interface MarketData extends AlgoHistoricalValuationService.ClosingPrices {
    List<Historyquote> history(Integer security, LocalDate through);

    @Override
    default Double close(Integer security, LocalDate through) {
      var observations = history(security, through);
      return observations.isEmpty() ? null : observations.getLast().getClose();
    }
  }

  /**
   * Actual fills of a lifecycle, including fills in the rolling 30-calendar-day window.
   *
   * <p>
   * {@code initialUnits} is the size the lifecycle was opened with and {@code realizedExitUnits} how much of it has
   * already been given back. Both are reconstructed from the ledger like every other component here, and together they
   * are what tells the profit taking module which tranches are still outstanding.
   * </p>
   */
  public record Position(long lifecycle, double signedUnits, double initialUnits, double realizedExitUnits,
      double initialPrice, double averagePrice, LocalDate lastEntry, LocalDate lastExit, int tradesIn30Days,
      int executedAdds, List<PositionFill> fills) {
    public Position(long lifecycle, double signedUnits, double initialUnits, double realizedExitUnits,
        double initialPrice, double averagePrice, LocalDate lastEntry, LocalDate lastExit, int tradesIn30Days) {
      this(lifecycle, signedUnits, initialUnits, realizedExitUnits, initialPrice, averagePrice, lastEntry, lastExit,
          tradesIn30Days, 0, List.of());
    }

    public static Position empty() {
      return new Position(0, 0, 0, 0, 0, 0, null, null, 0);
    }
  }

  /** Split-adjusted executed change, with optional frozen tranche quantities from its signal. */
  public record PositionFill(double delta, Map<String, Double> trancheTargets) {
  }

  /** Amounts and unit exposure are in tenant currency; price references are in instrument quotation units. */
  public record Context(Integer tenant, Integer strategy, Integer security, LocalDate date, MarketData market,
      Position position, double equity, double topCapacity, double bucketCapacity, double securityCapacity,
      double securityExposure, double unitExposure, boolean assignmentResolved, double reservedTop,
      double reservedBucket, double reservedSecurity) {
    public Context(Integer tenant, Integer strategy, Integer security, LocalDate date, MarketData market,
        Position position, double equity, double topCapacity, double bucketCapacity, double securityCapacity,
        double securityExposure, double unitExposure, boolean assignmentResolved) {
      this(tenant, strategy, security, date, market, position, equity, topCapacity, bucketCapacity, securityCapacity,
          securityExposure, unitExposure, assignmentResolved, 0, 0, 0);
    }
  }

  /**
   * Stable identity is separate from notification delivery and never serves as evidence of a fill.
   *
   * <p>
   * {@code tranche} names the profit taking tranche a partial exit belongs to and is empty for every other action. It
   * is part of the identity, so two tranches are two signals whose fills are counted apart, and it is what the alarm
   * row carries in {@code tranche_key}. The rationale stays a bare key, because the client resolves it as one.
   * </p>
   */
  public record Decision(Action action, int direction, double quantity, double price, String rationale, String identity,
      String tranche, Map<String, Double> trancheTargets) {
    public Decision(Action action, int direction, double quantity, double price, String rationale, String identity,
        String tranche) {
      this(action, direction, quantity, price, rationale, identity, tranche, Map.of());
    }

    public boolean increasesExposure() {
      return action == Action.ENTRY || action == Action.ADD;
    }

    public boolean actionable() {
      return increasesExposure() || action == Action.SCALE_OUT || action == Action.STOP_EXIT
          || action == Action.TAKE_PROFIT_EXIT || action == Action.RISK_EXIT;
    }
  }

  public Decision evaluate(StrategyConfig config, Context c) {
    MeanReversionConfigValidator.validate(config);
    try {
      List<Historyquote> history = c.market.history(c.security, c.date);
      if (history == null || history.isEmpty())
        return result(c, Action.UNAVAILABLE, 0, 0, 0, "MEAN_REVERSION_NO_HISTORY");
      LocalDate previous = null;
      for (Historyquote h : history) {
        if (h.getDate() == null || h.getDate().isAfter(c.date) || previous != null && !h.getDate().isAfter(previous)
            || !Double.isFinite(h.getClose()) || h.getClose() <= 0)
          return result(c, Action.UNAVAILABLE, 0, 0, 0, "MEAN_REVERSION_INVALID_HISTORY");
        previous = h.getDate();
      }
      double price = history.getLast().getClose();
      if (!history.getLast().getDate().equals(c.date))
        return result(c, Action.UNAVAILABLE, 0, 0, price, "MEAN_REVERSION_NO_CLOSE");
      if (!c.assignmentResolved)
        return result(c, Action.UNAVAILABLE, 0, 0, price, "MEAN_REVERSION_ASSIGNMENT_REQUIRED");
      Position p = c.position;
      int direction = p.signedUnits < 0 ? -1 : 1;
      double quantity = Math.abs(p.signedUnits);
      if (quantity > 0) {
        if (!positive(p.averagePrice) || !positive(p.initialPrice))
          return result(c, Action.UNAVAILABLE, direction, 0, price, "MEAN_REVERSION_COST_UNAVAILABLE");
        var risk = config.risk_controls;
        double gain = direction * (price / p.averagePrice - 1);
        boolean breached = c.equity <= 0 || c.topCapacity < -1e-8 || c.bucketCapacity < -1e-8
            || c.securityCapacity < -1e-8 || c.securityExposure > c.equity * risk.max_position_exposure_pct
            || gain <= -risk.max_position_drawdown_pct + 1e-12;
        if (breached && Boolean.TRUE.equals(risk.force_exit_on_risk_breach))
          return result(c, Action.RISK_EXIT, direction, quantity, price, "MEAN_REVERSION_RISK_EXIT");
        var downside = config.downside_management;
        var stop = downside.variant_A_sell_loss;
        boolean averaging = downside.loss_action == LossAction.B_average_down;
        if (!averaging && stop.stop_type == StopType.hard_stop
            && change(price, p, stop.stop_reference, direction) <= stop.stop_threshold_pct + 1e-12)
          return result(c, Action.STOP_EXIT, direction, quantity, price, "MEAN_REVERSION_STOP_EXIT");
        var t = downside.trigger;
        boolean threshold = change(price, p, t.down_reference, direction) <= t.down_threshold_pct + 1e-12;
        if (!averaging && downsideTriggered(t, history, threshold))
          return result(c, Action.STOP_EXIT, direction, quantity, price, "MEAN_REVERSION_DOWNSIDE_EXIT");
        var profit = config.profit_management == null ? null : config.profit_management.take_profit;
        if (profit != null) {
          double profitChange = change(price, p, profit.reference, direction);
          double base = profit.reference == ReferencePrice.avg_cost ? p.averagePrice : p.initialPrice;
          boolean take = profit.mode == TriggerType.pct_gain ? profitChange >= profit.pct - 1e-12
              : profitChange * base * quantity * c.unitExposure / price >= profit.profit_amount;
          if (take)
            return result(c, Action.TAKE_PROFIT_EXIT, direction, quantity, price, "MEAN_REVERSION_TAKE_PROFIT_EXIT");
        }
        // A partial exit is considered only after every full exit, so the more risk reducing action always wins.
        var partial = scaleOut.evaluate(config.profit_management, p, price, direction, c.unitExposure,
            (type, period) -> indicator(type.toLowerCase(Locale.ROOT), period, history));
        if (partial.isPresent()) {
          var decision = result(c, Action.SCALE_OUT, direction, partial.get().quantity(), price,
              partial.get().remainder() ? "SCALE_OUT_REMAINDER" : "SCALE_OUT", partial.get().tranche());
          return new Decision(decision.action(), direction, decision.quantity(), price, decision.rationale(),
              decision.identity(), decision.tranche(), partial.get().targets());
        }
        if (averaging) {
          var add = new AlgoAverageDownModule().evaluate(config, c, price,
              threshold && downsideTriggered(t, history, threshold), breached);
          return result(c, add.reason() == null ? Action.ADD : add.blocked() ? Action.BLOCKED : Action.HOLD, direction,
              add.quantity(), price, add.reason() == null ? "AVERAGE_DOWN_ADD" : add.reason());
        }
        return result(c, Action.HOLD, direction, 0, price, "MEAN_REVERSION_HOLD");
      }
      if (p.lastEntry != null && c.date.isBefore(p.lastEntry.plusDays(config.cooldowns.after_buy_days))
          || p.lastExit != null && c.date.isBefore(p.lastExit.plusDays(config.cooldowns.after_sell_days))
          || p.tradesIn30Days >= config.cooldowns.max_trades_per_asset_per_30d)
        return result(c, Action.BLOCKED, 0, 0, price, "MEAN_REVERSION_COOLDOWN");
      List<Historyquote> prior = history.subList(0, history.size() - 1);
      boolean longEntry = config.universe.direction != Direction.short_only
          && price / reference(config, prior, 1) - 1 <= config.entry.dip_threshold_pct + 1e-12;
      boolean shortEntry = config.universe.direction != Direction.long_only
          && -(price / reference(config, prior, -1) - 1) <= config.entry.dip_threshold_pct + 1e-12;
      if (!longEntry && !shortEntry)
        return result(c, Action.HOLD, 0, 0, price, "MEAN_REVERSION_NO_ENTRY");
      if (longEntry && shortEntry)
        return result(c, Action.BLOCKED, 0, 0, price, "MEAN_REVERSION_CONFLICTING_ENTRY");
      direction = longEntry ? 1 : -1;
      var sizing = config.entry.initial_buy_sizing;
      double amount = sizing.mode == SizingMode.absolute_amount ? sizing.amount : c.equity * sizing.pct;
      double capacity = Math.min(Math.min(c.topCapacity - c.reservedTop, c.bucketCapacity - c.reservedBucket),
          Math.min(c.securityCapacity - c.reservedSecurity,
              c.equity * config.risk_controls.max_position_exposure_pct - c.securityExposure - c.reservedSecurity));
      if (!positive(c.equity) || !positive(c.unitExposure) || !Double.isFinite(capacity) || amount > capacity + 1e-8)
        return result(c, Action.BLOCKED, direction, 0, price, "MEAN_REVERSION_BUDGET_EXHAUSTED");
      return result(c, Action.ENTRY, direction, amount / c.unitExposure, price, "MEAN_REVERSION_ENTRY");
    } catch (Unavailable e) {
      return result(c, Action.UNAVAILABLE, 0, 0, 0, e.getMessage());
    }
  }

  private boolean downsideTriggered(grafioschtrader.algo.strategy.model.complex.downside.DownsideTriggerConfig t,
      List<Historyquote> history, boolean threshold) {
    return switch (t.decision_basis) {
    case simple_threshold -> threshold;
    case indicator -> indicatorRules(t.indicator_rules, history);
    case statistical -> statisticalRules(t.statistical_rules, history);
    case hybrid -> threshold && indicatorRules(t.indicator_rules, history)
        && statisticalRules(t.statistical_rules, history);
    };
  }

  private double reference(StrategyConfig c, List<Historyquote> prior, int direction) {
    var r = c.entry.dip_reference;
    int period = r.type == DipReferenceType.price_T_ago ? c.entry.lookback_T : r.period;
    if (prior.size() < period)
      throw new Unavailable("MEAN_REVERSION_NO_HISTORY");
    return switch (r.type) {
    case price_T_ago -> prior.get(prior.size() - period).getClose();
    case highest_in_window -> prior.subList(prior.size() - period, prior.size()).stream()
        .mapToDouble(Historyquote::getClose).reduce(direction > 0 ? Math::max : Math::min).orElseThrow();
    case moving_average -> indicator(r.indicator.toLowerCase(Locale.ROOT), period, prior);
    };
  }

  private boolean indicatorRules(List<grafioschtrader.algo.strategy.model.complex.downside.IndicatorRuleConfig> rules,
      List<Historyquote> history) {
    boolean result = true;
    for (var r : rules)
      result &= rule(r.type.name(), r.params, history);
    return result;
  }

  private boolean statisticalRules(
      List<grafioschtrader.algo.strategy.model.complex.downside.StatisticalRuleConfig> rules,
      List<Historyquote> history) {
    boolean result = true;
    for (var r : rules)
      result &= rule(r.type, r.params, history);
    return result;
  }

  private boolean rule(String type, IndicatorParams p, List<Historyquote> history) {
    double value = indicator(type, "zscore".equals(type) ? p.lookback : p.length, history);
    return switch (p.condition) {
    case "<" -> value < p.value;
    case "<=" -> value <= p.value;
    case ">" -> value > p.value;
    case ">=" -> value >= p.value;
    case "==" -> value == p.value;
    case "!=" -> value != p.value;
    default -> throw new IllegalArgumentException("Unsupported comparison");
    };
  }

  private double indicator(String type, int period, List<Historyquote> history) {
    if (history.size() < period)
      throw new Unavailable("MEAN_REVERSION_NO_HISTORY");
    if ("zscore".equals(type)) {
      var values = history.subList(history.size() - period, history.size());
      double mean = values.stream().mapToDouble(Historyquote::getClose).average().orElseThrow();
      double variance = values.stream().mapToDouble(h -> Math.pow(h.getClose() - mean, 2)).average().orElseThrow();
      if (variance <= 0)
        throw new Unavailable("MEAN_REVERSION_ZERO_VARIANCE");
      return (history.getLast().getClose() - mean) / Math.sqrt(variance);
    }
    try {
      double value = AlertExpressionSupport.create(type.toUpperCase(Locale.ROOT) + "(" + period + ")", history)
          .evaluate().getNumberValue().doubleValue();
      if (!Double.isFinite(value))
        throw new Unavailable("MEAN_REVERSION_INDICATOR_UNAVAILABLE");
      return value;
    } catch (Exception e) {
      throw new Unavailable("MEAN_REVERSION_INDICATOR_UNAVAILABLE");
    }
  }

  private static boolean positive(double value) {
    return Double.isFinite(value) && value > 0;
  }

  private static double change(double price, Position p, ReferencePrice ref, int direction) {
    return direction * (price / (ref == ReferencePrice.avg_cost ? p.averagePrice : p.initialPrice) - 1);
  }

  private Decision result(Context c, Action action, int direction, double units, double price, String reason) {
    return result(c, action, direction, units, price, reason, AlgoMessageAlert.NO_TRANCHE);
  }

  /**
   * The tranche is appended only when there is one, so every identity the strategy has already written stays unchanged.
   */
  private Decision result(Context c, Action action, int direction, double units, double price, String reason,
      String tranche) {
    return new Decision(action, direction, units, price, reason,
        c.tenant + ":" + c.strategy + ":" + c.security + ":" + c.position.lifecycle + ":" + c.date + ":" + action + ":"
            + direction + (tranche.isEmpty() ? "" : ":" + tranche),
        tranche);
  }

  private static class Unavailable extends RuntimeException {
    private static final long serialVersionUID = 1L;

    Unavailable(String reason) {
      super(reason);
    }
  }
}
