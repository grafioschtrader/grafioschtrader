package grafioschtrader.algo.strategy.model.complex.profit;

import java.util.List;

import grafioschtrader.algo.strategy.model.complex.downside.IndicatorRuleConfig;
import grafioschtrader.algo.strategy.model.complex.enums.ReferencePrice;
import grafioschtrader.algo.strategy.model.complex.enums.TriggerType;
import jakarta.validation.Valid;

/**
 * Reusable trigger definition: specifies the type, threshold value, and price reference for triggering an event.
 *
 * <p>
 * A {@code pct_gain} or {@code absolute_profit} trigger is described by {@code value} and {@code reference}. An
 * {@code indicator} trigger instead carries {@code indicator_rules}, which reuses the rule shape of the downside module
 * so that one indicator vocabulary serves both, and fires only when every rule holds.
 * </p>
 */

public class TriggerConfig {

  public TriggerType type;

  public Double value;

  public ReferencePrice reference;

  @Valid
  public List<@Valid IndicatorRuleConfig> indicator_rules;
}
