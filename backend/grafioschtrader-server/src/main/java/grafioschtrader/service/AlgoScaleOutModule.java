package grafioschtrader.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import grafioschtrader.algo.strategy.model.complex.downside.IndicatorRuleConfig;
import grafioschtrader.algo.strategy.model.complex.enums.IndicatorType;
import grafioschtrader.algo.strategy.model.complex.enums.ReferencePrice;
import grafioschtrader.algo.strategy.model.complex.enums.SellFractionBasis;
import grafioschtrader.algo.strategy.model.complex.enums.TriggerType;
import grafioschtrader.algo.strategy.model.complex.profit.ProfitManagementConfig;
import grafioschtrader.algo.strategy.model.complex.profit.ScaleOutTrancheConfig;
import grafioschtrader.algo.strategy.model.complex.profit.TriggerConfig;
import grafioschtrader.service.AlgoMeanReversionDecisionService.Position;

/**
 * Partial profit taking, independent of how the position was entered.
 *
 * <p>
 * The module knows a plan of tranches and a position; it knows nothing about dips, breakouts or any other entry rule,
 * which is what lets a later strategy reuse it unchanged. Like the decision service that calls it, it reads neither a
 * quote, nor the wall clock, nor a repository.
 * </p>
 *
 * <p>
 * Outstanding tranches are reconstructed from executed fills. Current-position targets freeze on their first fill;
 * later additions affect only unstarted tranches. Partial fills re-propose only the remainder, and completed tranches
 * stay completed after additions or a restart. Notifications never enter this calculation.
 * </p>
 */
@Service
public class AlgoScaleOutModule {

  /** Quantities are compared in instrument units, where a difference this small is rounding, not a position. */
  private static final double UNIT_EPSILON = 1e-8;

  /** Price thresholds are compared inclusively despite binary rounding, as elsewhere in the decision path. */
  private static final double PRICE_EPSILON = 1e-12;

  /**
   * Indicator values the calling strategy computes from its own observations.
   *
   * <p>
   * Supplying them rather than the price history keeps the module free of any assumption about how a strategy obtains
   * its market data, and lets a test drive it with hand written values.
   * </p>
   */
  @FunctionalInterface
  public interface Indicators {
    /**
     * Returns the value of one indicator at the decision time.
     *
     * @param type   indicator name, for example {@code rsi} or {@code sma}
     * @param period number of observations the indicator measures over
     * @return the indicator value
     */
    double value(String type, int period);
  }

  /** One executable step of the plan: the tranche that fired and how much of the position it gives back. */
  public record ScaleOut(String tranche, double quantity, boolean remainder, Map<String, Double> targets) {
    public ScaleOut(String tranche, double quantity, boolean remainder) {
      this(tranche, quantity, remainder, Map.of());
    }
  }

  /**
   * Decides whether the profit taking plan gives back part of the position at this price.
   *
   * <p>
   * The plan is walked in order from the first outstanding tranche and advances while the next tranche also triggers,
   * so a single large favourable move can settle several tranches at once without ever skipping one. The resulting
   * quantity is the cumulative plan up to that tranche minus what has already been realised, clamped to the position
   * that is actually still open, so no combination of tranches, partial fills and a final liquidation can close more
   * than there is.
   * </p>
   *
   * @param config       the profit management section, or null when the strategy takes no partial profit
   * @param position     the current lifecycle as reconstructed from the ledger
   * @param price        the closing price the decision is taken at, in instrument quotation units
   * @param direction    1 for a long position, -1 for a short one
   * @param unitExposure value of one unit in tenant currency, used by an absolute profit trigger
   * @param indicators   indicator values for an indicator trigger
   * @return the tranche to execute, or empty when none is due
   */
  public Optional<ScaleOut> evaluate(ProfitManagementConfig config, Position position, double price, int direction,
      double unitExposure, Indicators indicators) {
    if (config == null || !Boolean.TRUE.equals(config.scale_out_enabled) || config.scale_out_plan == null
        || config.scale_out_plan.isEmpty()) {
      return Optional.empty();
    }
    double open = Math.abs(position.signedUnits());
    double realized = position.realizedExitUnits();
    // A lifecycle recorded before the plan was configured has no opening size of its own; what is still open plus
    // what has already been given back is the same number whenever nothing was added to the position.
    double initial = position.initialUnits() > 0 ? position.initialUnits() : open + realized;
    if (open <= UNIT_EPSILON || initial <= UNIT_EPSILON) {
      return Optional.empty();
    }

    List<ScaleOutTrancheConfig> plan = config.scale_out_plan;
    if (!position.fills().isEmpty())
      return fromFills(config, position, price, direction, unitExposure, indicators);
    double[] cumulative = cumulativeQuantities(plan, config.sell_fraction_basis, initial);
    int first = 0;
    while (first < plan.size() && realized >= cumulative[first] - UNIT_EPSILON) {
      first++;
    }
    if (first == plan.size()
        || !triggered(plan.get(first), position, price, direction, open, unitExposure, indicators)) {
      return Optional.empty();
    }
    int last = first;
    while (last + 1 < plan.size()
        && triggered(plan.get(last + 1), position, price, direction, open, unitExposure, indicators)) {
      last++;
    }

    ScaleOutTrancheConfig tranche = plan.get(last);
    boolean remainder = Boolean.TRUE.equals(tranche.sell_remainder);
    double quantity = remainder ? open : Math.min(open, cumulative[last] - realized);
    return quantity > UNIT_EPSILON ? Optional.of(new ScaleOut(tranche.id, quantity, remainder)) : Optional.empty();
  }

  /** Replays reductions in order, freezing current-position targets only once an actual fill consumes them. */
  private Optional<ScaleOut> fromFills(ProfitManagementConfig config, Position position, double price, int direction,
      double unitExposure, Indicators indicators) {
    var plan = config.scale_out_plan;
    double[] targets = new double[plan.size()];
    double[] executed = new double[plan.size()];
    java.util.Arrays.fill(targets, Double.NaN);
    double holding = 0;
    int first = 0;
    for (var fill : position.fills()) {
      double delta = fill.delta() * direction;
      if (delta > 0) {
        holding += delta;
        continue;
      }
      for (int i = first; i < plan.size(); i++) {
        Double frozen = fill.trancheTargets().get(plan.get(i).id);
        if (frozen != null && Double.isNaN(targets[i]))
          targets[i] = frozen;
      }
      double reduction = -delta;
      while (reduction > UNIT_EPSILON && first < plan.size()) {
        var tranche = plan.get(first);
        if (Double.isNaN(targets[first])) {
          targets[first] = Boolean.TRUE.equals(tranche.sell_remainder) ? Double.POSITIVE_INFINITY
              : fill.trancheTargets().getOrDefault(tranche.id,
                  tranche.sell_fraction
                      * (config.sell_fraction_basis == SellFractionBasis.initial_position ? position.initialUnits()
                          : holding));
        }
        double used = Math.min(reduction, Math.max(0, targets[first] - executed[first]));
        executed[first] += used;
        reduction -= used;
        holding -= used;
        if (executed[first] >= targets[first] - UNIT_EPSILON)
          first++;
        else
          break;
      }
      holding -= reduction;
    }
    double available = Math.abs(position.signedUnits());
    double quantity = 0;
    String last = null;
    boolean remainder = false;
    Map<String, Double> frozen = new LinkedHashMap<>();
    for (int i = first; i < plan.size() && available > UNIT_EPSILON; i++) {
      var tranche = plan.get(i);
      if (!triggered(tranche, position, price, direction, available, unitExposure, indicators))
        break;
      remainder = Boolean.TRUE.equals(tranche.sell_remainder);
      double target = Double.isNaN(targets[i])
          ? (remainder ? available
              : tranche.sell_fraction
                  * (config.sell_fraction_basis == SellFractionBasis.initial_position ? position.initialUnits()
                      : available))
          : targets[i];
      double outstanding = remainder ? available : Math.min(available, Math.max(0, target - executed[i]));
      if (!remainder)
        frozen.put(tranche.id, target);
      quantity += outstanding;
      available -= outstanding;
      last = tranche.id;
    }
    return quantity > UNIT_EPSILON ? Optional.of(new ScaleOut(last, quantity, remainder, Map.copyOf(frozen)))
        : Optional.empty();
  }

  /**
   * Quantity the plan has given back once each tranche has executed, in instrument units.
   *
   * <p>
   * On the initial basis every fraction is measured against the opening size, so the cumulative quantities are the
   * running sum. On the current basis a fraction is measured against what is left when the tranche fires, so what
   * remains after each tranche is the product of the complementary fractions. A tranche that closes the remainder can
   * never be satisfied by a quantity, which is what keeps it outstanding until the position itself is gone.
   * </p>
   *
   * @param plan    the tranches in the order they execute
   * @param basis   whether a fraction is measured against the initial or the then current position
   * @param initial size the lifecycle was opened with, in instrument units
   * @return cumulative quantities by tranche index
   */
  private static double[] cumulativeQuantities(List<ScaleOutTrancheConfig> plan, SellFractionBasis basis,
      double initial) {
    double[] cumulative = new double[plan.size()];
    double sum = 0;
    double remaining = 1;
    for (int i = 0; i < plan.size(); i++) {
      ScaleOutTrancheConfig tranche = plan.get(i);
      if (Boolean.TRUE.equals(tranche.sell_remainder)) {
        cumulative[i] = Double.POSITIVE_INFINITY;
      } else if (basis == SellFractionBasis.initial_position) {
        sum += tranche.sell_fraction;
        cumulative[i] = initial * sum;
      } else {
        remaining *= 1 - tranche.sell_fraction;
        cumulative[i] = initial * (1 - remaining);
      }
    }
    return cumulative;
  }

  private static boolean triggered(ScaleOutTrancheConfig tranche, Position position, double price, int direction,
      double open, double unitExposure, Indicators indicators) {
    TriggerConfig trigger = tranche.trigger;
    if (trigger.type == TriggerType.indicator) {
      return trigger.indicator_rules.stream().allMatch(rule -> holds(rule, indicators));
    }
    double base = trigger.reference == ReferencePrice.avg_cost ? position.averagePrice() : position.initialPrice();
    double change = direction * (price / base - 1);
    // The same two forms the full take-profit uses, so a tranche and a full exit cannot read one price differently.
    return trigger.type == TriggerType.pct_gain ? change >= trigger.value - PRICE_EPSILON
        : change * base * open * unitExposure / price >= trigger.value;
  }

  private static boolean holds(IndicatorRuleConfig rule, Indicators indicators) {
    var p = rule.params;
    boolean statistical = rule.type == IndicatorType.zscore;
    double value = indicators.value(rule.type.name(), statistical ? p.lookback : p.length);
    return switch (p.condition) {
    case "<" -> value < p.value;
    case "<=" -> value <= p.value;
    case ">" -> value > p.value;
    case ">=" -> value >= p.value;
    case "==" -> value == p.value;
    default -> value != p.value;
    };
  }
}
