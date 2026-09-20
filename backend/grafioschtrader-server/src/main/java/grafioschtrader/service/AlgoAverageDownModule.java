package grafioschtrader.service;

import grafioschtrader.algo.strategy.model.complex.StrategyConfig;
import grafioschtrader.algo.strategy.model.complex.enums.*;
import grafioschtrader.service.AlgoMeanReversionDecisionService.Context;

/** Pure addition sizing and step checks; observations and executed position state are supplied by the caller. */
public final class AlgoAverageDownModule {
  /** A null reason denotes an executable addition; other outcomes distinguish waiting from blocked actions. */
  public record Addition(double quantity, String reason, boolean blocked) {
  }

  /** Evaluates one addition after exits and confirmation rules have been evaluated by the caller. */
  public Addition evaluate(StrategyConfig config, Context c, double price, boolean confirmed, boolean riskBreached) {
    var a = config.downside_management.variant_B_average_down;
    var p = c.position();
    if (!confirmed)
      return new Addition(0, "AVERAGE_DOWN_NO_ADD", false);
    if (riskBreached)
      return new Addition(0, "AVERAGE_DOWN_RISK_BLOCK", true);
    if (p.executedAdds() >= a.max_adds)
      return new Addition(0, "AVERAGE_DOWN_MAX_ADDS", true);
    if (p.lastEntry() != null && c.date().isBefore(p.lastEntry().plusDays(config.cooldowns.after_buy_days))
        || p.lastExit() != null && c.date().isBefore(p.lastExit().plusDays(config.cooldowns.after_sell_days))
        || p.tradesIn30Days() >= config.cooldowns.max_trades_per_asset_per_30d)
      return new Addition(0, "MEAN_REVERSION_COOLDOWN", true);
    var step = a.add_step_rule;
    if (step.type == AddStepType.each_n_pct_drop) {
      double reference = step.reference == ReferencePrice.avg_cost ? p.averagePrice() : p.initialPrice();
      double limit = step.reference == ReferencePrice.avg_cost ? -step.drop_pct_step
          : config.downside_management.trigger.down_threshold_pct - p.executedAdds() * step.drop_pct_step;
      double change = Math.signum(p.signedUnits()) * (price / reference - 1);
      if (change > limit + 1e-12)
        return new Addition(0, "AVERAGE_DOWN_NEXT_STEP", false);
    }
    double amount = a.add_sizing.mode == SizingMode.absolute_amount ? a.add_sizing.amount
        : c.equity() * a.add_sizing.pct;
    double capacity = Math.min(Math.min(c.topCapacity() - c.reservedTop(), c.bucketCapacity() - c.reservedBucket()),
        Math.min(c.securityCapacity() - c.reservedSecurity(),
            c.equity() * config.risk_controls.max_position_exposure_pct - c.securityExposure() - c.reservedSecurity()));
    if (!Double.isFinite(c.equity()) || c.equity() <= 0 || !Double.isFinite(c.unitExposure()) || c.unitExposure() <= 0
        || !Double.isFinite(capacity) || amount > capacity + 1e-8)
      return new Addition(0, "MEAN_REVERSION_BUDGET_EXHAUSTED", true);
    return new Addition(amount / c.unitExposure(), null, false);
  }
}
