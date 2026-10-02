package grafioschtrader.algo.strategy.model.complex;

import static grafioschtrader.algo.strategy.model.complex.ConfigChecks.*;

import java.util.Set;

import grafioschtrader.algo.strategy.model.complex.downside.DownsideManagementConfig;
import grafioschtrader.algo.strategy.model.complex.enums.*;

/**
 * Executable daily-close contract. Drafts retain the wider configuration model for later modules.
 *
 * <p>
 * The contract demands only settings that influence a decision. The entry type may be omitted because
 * {@link grafioschtrader.algo.strategy.model.complex.enums.EntryType#dip_buy} is its only value. Settings with a single
 * executable value (data source, execution, the exit action) and settings the engine never reads (universe mode,
 * version, outputs) may be omitted as well; when present they must name that single value, so configurations written
 * against the earlier, stricter contract remain executable. The loss action alone selects the downside variant; its
 * {@code enabled} flag may be omitted and only contradicts the choice when it is explicitly false.
 * </p>
 */
public final class MeanReversionConfigValidator {
  private MeanReversionConfigValidator() {
  }

  public static void validate(StrategyConfig c) {
    require(c != null, "Configuration is required");
    require(c.universe != null && c.universe.direction != null, "universe.direction is required");
    require(c.universe.assets == null || c.universe.assets.isEmpty(), "Instruments come from the hierarchy watchlist");
    validateFixedSettings(c);
    validateEntry(c);
    require(
        c.cooldowns != null && c.cooldowns.after_buy_days != null && c.cooldowns.after_buy_days >= 0
            && c.cooldowns.after_sell_days != null && c.cooldowns.after_sell_days >= 0
            && c.cooldowns.max_trades_per_asset_per_30d != null && c.cooldowns.max_trades_per_asset_per_30d > 0,
        "Non-negative cooldowns and a positive trade limit are required");
    validateDownside(c.downside_management);
    // The profit management module is entry-agnostic and owns its own contract.
    ProfitManagementValidator.validate(c.profit_management);
    require(c.risk_controls != null, "Risk controls are required");
    fraction(c.risk_controls.max_position_exposure_pct, "max_position_exposure_pct");
    fraction(c.risk_controls.max_position_drawdown_pct, "max_position_drawdown_pct");
    require(c.risk_controls.force_exit_on_risk_breach != null, "force_exit_on_risk_breach is required");
  }

  /** Data source and execution have one executable value each; both sections may be omitted. */
  private static void validateFixedSettings(StrategyConfig c) {
    require(c.data == null || nullOr(c.data.price_field, PriceField.close) && nullOr(c.data.timeframe, "1d"),
        "Mean reversion requires daily close data (close, 1d)");
    require(
        c.execution == null || nullOr(c.execution.order_type, OrderType.market)
            && nullOr(c.execution.fees_model, "none") && nullOr(c.execution.slippage_model, "none"),
        "Mean reversion supports market orders with none fees and slippage models");
  }

  private static void validateEntry(StrategyConfig c) {
    require(c.entry != null && nullOr(c.entry.type, EntryType.dip_buy) && c.entry.dip_reference != null
        && c.entry.dip_reference.type != null, "A dip entry and reference are required");
    negativeFraction(c.entry.dip_threshold_pct, "entry.dip_threshold_pct");
    period(c.entry.lookback_T);
    if (c.entry.dip_reference.type != DipReferenceType.price_T_ago)
      period(c.entry.dip_reference.period);
    if (c.entry.dip_reference.type == DipReferenceType.moving_average)
      require(Set.of("SMA", "EMA").contains(String.valueOf(c.entry.dip_reference.indicator)), "Use SMA or EMA");
    sizing(c.entry.initial_buy_sizing);
  }

  /**
   * Validates the selected loss variant and the downside trigger. The trigger is optional for a hard stop, whose own
   * threshold already closes the position; averaging and an indicator stop need it.
   */
  private static void validateDownside(DownsideManagementConfig d) {
    require(d != null && d.loss_action != null, "A downside action is required");
    boolean indicatorStop = false;
    if (d.loss_action == LossAction.A_sell_loss) {
      require(d.variant_B_average_down == null || !Boolean.TRUE.equals(d.variant_B_average_down.enabled),
          "Disable averaging when sell-loss is selected");
      var a = d.variant_A_sell_loss;
      require(
          a != null && !Boolean.FALSE.equals(a.enabled) && a.stop_type != null && a.stop_reference != null
              && nullOr(a.order_type, OrderType.market) && nullOr(a.action, "sell_all_remaining"),
          "A full market stop exit is required");
      negativeFraction(a.stop_threshold_pct, "stop_threshold_pct");
      indicatorStop = a.stop_type == StopType.indicator_stop;
    } else {
      require(d.trigger != null, "Averaging requires a downside trigger");
      AverageDownValidator.validate(d);
    }
    require(d.trigger != null || !indicatorStop, "An indicator stop requires indicator or statistical rules");
    if (d.trigger != null)
      validateTrigger(d, indicatorStop);
  }

  private static void validateTrigger(DownsideManagementConfig d, boolean indicatorStop) {
    require(d.trigger.decision_basis != null && d.trigger.down_reference != null,
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
    require(!indicatorStop || basis != DecisionBasis.simple_threshold,
        "An indicator stop requires indicator or statistical rules");
  }

  static void sizing(SizingConfig s) {
    require(s != null && s.mode != null, "Entry sizing is required");
    if (s.mode == SizingMode.pct_portfolio)
      fraction(s.pct, "sizing.pct");
    else
      positive(s.amount, "sizing.amount");
  }
}
