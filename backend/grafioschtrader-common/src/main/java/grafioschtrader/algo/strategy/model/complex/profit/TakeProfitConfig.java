package grafioschtrader.algo.strategy.model.complex.profit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafioschtrader.algo.strategy.model.complex.enums.ReferencePrice;
import grafioschtrader.algo.strategy.model.complex.enums.TriggerType;

/**
 * Final take-profit configuration: triggers a full position exit when the gain target is reached.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TakeProfitConfig {

  public TriggerType mode;

  public Double pct;

  public Double profit_amount;

  public ReferencePrice reference;

  public String action;
}
