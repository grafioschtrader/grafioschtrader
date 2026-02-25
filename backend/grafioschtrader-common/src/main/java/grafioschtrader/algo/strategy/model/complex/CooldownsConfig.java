package grafioschtrader.algo.strategy.model.complex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Min;

/**
 * Cooldown periods to prevent over-trading after buy or sell events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CooldownsConfig {

  @Min(0)
  public Integer after_buy_days;

  @Min(0)
  public Integer after_sell_days;

  @Min(0)
  public Integer max_trades_per_asset_per_30d;
}
