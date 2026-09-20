package grafioschtrader.algo.strategy.model.complex;

import static grafioschtrader.algo.strategy.model.complex.ConfigChecks.*;

import java.util.Set;

import grafioschtrader.algo.strategy.model.complex.enums.*;

/** Executable daily-close contract. Drafts retain the wider configuration model for later modules. */
public final class MeanReversionConfigValidator {
  private MeanReversionConfigValidator() {
  }

  public static void validate(StrategyConfig c) {
    require(c != null, "Configuration is required");
    require(c.universe != null && c.universe.direction != null && c.universe.mode != null,
        "universe mode and direction are required");
    require(c.universe.assets == null || c.universe.assets.isEmpty(), "Instruments come from the hierarchy watchlist");
    require(c.data != null && c.data.price_field == PriceField.close && "1d".equals(c.data.timeframe),
        "Mean reversion requires daily close data (close, 1d)");
    require(
        c.execution != null && c.execution.order_type == OrderType.market && "none".equals(c.execution.fees_model)
            && "none".equals(c.execution.slippage_model),
        "Mean reversion supports market orders with none fees and slippage models");
    require(c.entry != null && c.entry.type == EntryType.dip_buy && c.entry.dip_reference != null
        && c.entry.dip_reference.type != null, "A dip entry and reference are required");
    negativeFraction(c.entry.dip_threshold_pct, "entry.dip_threshold_pct");
    period(c.entry.lookback_T);
    if (c.entry.dip_reference.type != DipReferenceType.price_T_ago)
      period(c.entry.dip_reference.period);
    if (c.entry.dip_reference.type == DipReferenceType.moving_average)
      require(Set.of("SMA", "EMA").contains(String.valueOf(c.entry.dip_reference.indicator)), "Use SMA or EMA");
    sizing(c.entry.initial_buy_sizing);
    require(
        c.cooldowns != null && c.cooldowns.after_buy_days != null && c.cooldowns.after_buy_days >= 0
            && c.cooldowns.after_sell_days != null && c.cooldowns.after_sell_days >= 0
            && c.cooldowns.max_trades_per_asset_per_30d != null && c.cooldowns.max_trades_per_asset_per_30d > 0,
        "Non-negative cooldowns and a positive trade limit are required");
    var d = c.downside_management;
    require(d != null && d.loss_action != null, "A downside action is required");
    if (d.loss_action == LossAction.A_sell_loss) {
      require(d.variant_B_average_down == null || !Boolean.TRUE.equals(d.variant_B_average_down.enabled),
          "Disable averaging when sell-loss is selected");
      require(d.variant_A_sell_loss != null && Boolean.TRUE.equals(d.variant_A_sell_loss.enabled)
          && d.variant_A_sell_loss.stop_type != null && d.variant_A_sell_loss.stop_reference != null
          && d.variant_A_sell_loss.order_type == OrderType.market
          && "sell_all_remaining".equals(d.variant_A_sell_loss.action), "A full market stop exit is required");
      negativeFraction(d.variant_A_sell_loss.stop_threshold_pct, "stop_threshold_pct");
    } else {
      AverageDownValidator.validate(d);
    }
    require(d.trigger != null && d.trigger.decision_basis != null && d.trigger.down_reference != null,
        "Downside trigger and reference are required");
    negativeFraction(d.trigger.down_threshold_pct, "down_threshold_pct");
    var basis = d.trigger.decision_basis;
    if (basis == DecisionBasis.indicator || basis == DecisionBasis.hybrid) {
      require(d.trigger.indicator_rules != null && !d.trigger.indicator_rules.isEmpty(),
          "Indicator rules are required");
      d.trigger.indicator_rules.forEach(r -> {
        require(r != null && r.type != null, "Indicator type is required");
        params(r.params, r.type == IndicatorType.zscore);
      });
    }
    if (basis == DecisionBasis.statistical || basis == DecisionBasis.hybrid) {
      require(d.trigger.statistical_rules != null && !d.trigger.statistical_rules.isEmpty(),
          "Statistical rules are required");
      d.trigger.statistical_rules.forEach(r -> {
        require(r != null && "zscore".equals(r.type), "Only zscore statistical rules are supported");
        params(r.params, true);
      });
    }
    if (d.loss_action == LossAction.A_sell_loss && d.variant_A_sell_loss.stop_type == StopType.indicator_stop)
      require(basis != DecisionBasis.simple_threshold, "An indicator stop requires indicator or statistical rules");
    // The profit management module is entry-agnostic and owns its own contract.
    ProfitManagementValidator.validate(c.profit_management);
    require(c.risk_controls != null, "Risk controls are required");
    fraction(c.risk_controls.max_position_exposure_pct, "max_position_exposure_pct");
    fraction(c.risk_controls.max_position_drawdown_pct, "max_position_drawdown_pct");
    require(c.risk_controls.force_exit_on_risk_breach != null, "force_exit_on_risk_breach is required");
  }

  static void sizing(SizingConfig s) {
    require(s != null && s.mode != null, "Entry sizing is required");
    if (s.mode == SizingMode.pct_portfolio)
      fraction(s.pct, "sizing.pct");
    else
      positive(s.amount, "sizing.amount");
  }
}
