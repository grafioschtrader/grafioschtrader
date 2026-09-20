package grafioschtrader.algo.strategy.model.complex;

import static grafioschtrader.algo.strategy.model.complex.ConfigChecks.*;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import grafioschtrader.algo.strategy.model.complex.enums.IndicatorType;
import grafioschtrader.algo.strategy.model.complex.enums.SellFractionBasis;
import grafioschtrader.algo.strategy.model.complex.enums.TriggerType;
import grafioschtrader.algo.strategy.model.complex.profit.ProfitManagementConfig;
import grafioschtrader.algo.strategy.model.complex.profit.ScaleOutTrancheConfig;
import grafioschtrader.algo.strategy.model.complex.profit.TriggerConfig;

/**
 * Executable contract of the profit management module, independent of how a position was entered.
 *
 * <p>
 * The module is reusable, so its contract lives here rather than inside the validator of one entry strategy: any
 * strategy that offers partial profit taking validates it through this class and therefore accepts exactly the plans
 * the decision module can execute.
 * </p>
 *
 * <p>
 * The structure of a scale-out plan is checked whenever a plan is present, not only when it is switched on. A plan that
 * is carried along disabled is still a plan the user will eventually enable, and reporting its defect at the moment it
 * is written is more useful than reporting it much later.
 * </p>
 */
public final class ProfitManagementValidator {

  /** Bounded because the identifier is carried into the signal identity and into {@code tranche_key}. */
  private static final Pattern TRANCHE_ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");

  /** A plan long enough to be unreadable is far more likely to be a mistake than an intention. */
  private static final int MAX_TRANCHES = 20;

  private ProfitManagementValidator() {
  }

  /**
   * Validates the profit management section of an executable strategy.
   *
   * @param c the section, or null when the strategy takes no profit at all, which is permitted
   * @throws IllegalArgumentException with a user-facing explanation of the first defect found
   */
  public static void validate(ProfitManagementConfig c) {
    if (c == null)
      return;
    validateTakeProfit(c);
    boolean enabled = Boolean.TRUE.equals(c.scale_out_enabled);
    boolean planned = c.scale_out_plan != null && !c.scale_out_plan.isEmpty();
    require(!enabled || planned, "An enabled scale-out requires at least one tranche");
    require(!enabled || c.sell_fraction_basis != null, "An enabled scale-out requires sell_fraction_basis");
    if (planned)
      validatePlan(c);
  }

  private static void validateTakeProfit(ProfitManagementConfig c) {
    var p = c.take_profit;
    if (p == null)
      return;
    require(p.reference != null && "sell_all_remaining".equals(p.action),
        "Take-profit requires reference and full exit");
    require(p.mode == TriggerType.pct_gain || p.mode == TriggerType.absolute_profit,
        "Take-profit supports pct_gain or absolute_profit");
    positive(p.mode == TriggerType.pct_gain ? p.pct : p.profit_amount, "take_profit");
  }

  private static void validatePlan(ProfitManagementConfig c) {
    var plan = c.scale_out_plan;
    require(plan.size() <= MAX_TRANCHES, "A scale-out plan holds at most " + MAX_TRANCHES + " tranches");
    Set<String> ids = new HashSet<>();
    double total = 0;
    for (int i = 0; i < plan.size(); i++) {
      ScaleOutTrancheConfig t = plan.get(i);
      require(t != null && t.id != null && TRANCHE_ID.matcher(t.id).matches(),
          "Every tranche needs an identifier of letters, digits, '-' or '_', at most 32 characters");
      require(ids.add(t.id), "Tranche identifiers must be unique within the plan: " + t.id);
      boolean remainder = Boolean.TRUE.equals(t.sell_remainder);
      require(remainder != (t.sell_fraction != null),
          "A tranche either sells a fraction or the remainder, not both and not neither: " + t.id);
      // A tranche behind the one that closes everything could never execute, so the plan would not mean what it says.
      require(!remainder || i == plan.size() - 1, "Only the last tranche may sell the remainder: " + t.id);
      if (!remainder) {
        require(t.sell_fraction > 0 && t.sell_fraction < 1 && Double.isFinite(t.sell_fraction),
            "sell_fraction must be between 0 and 1, exclusive: " + t.id);
        total += t.sell_fraction;
      }
      validateTrigger(t.trigger, t.id);
    }
    require(c.sell_fraction_basis != SellFractionBasis.initial_position || total <= 1 + 1e-12,
        "Fractions of the initial position must not add up to more than the whole position");
  }

  private static void validateTrigger(TriggerConfig t, String tranche) {
    require(t != null && t.type != null, "Every tranche needs a trigger with a type: " + tranche);
    boolean indicator = t.type == TriggerType.indicator;
    require(indicator == (t.indicator_rules != null && !t.indicator_rules.isEmpty()),
        "An indicator trigger needs indicator_rules, another trigger type must not carry them: " + tranche);
    if (indicator) {
      require(t.value == null, "An indicator trigger takes its bounds from its rules, not from value: " + tranche);
      t.indicator_rules.forEach(r -> {
        require(r != null && r.type != null, "Indicator type is required");
        params(r.params, r.type == IndicatorType.zscore);
      });
    } else {
      require(t.reference != null, "A gain or profit trigger needs a price reference: " + tranche);
      positive(t.value, "trigger.value of " + tranche);
    }
  }
}
