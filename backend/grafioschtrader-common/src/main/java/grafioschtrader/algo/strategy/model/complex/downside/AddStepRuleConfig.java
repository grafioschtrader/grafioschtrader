package grafioschtrader.algo.strategy.model.complex.downside;

import grafioschtrader.algo.strategy.model.complex.enums.AddStepType;
import grafioschtrader.algo.strategy.model.complex.enums.ReferencePrice;

/**
 * Rule defining the interval at which additional buy orders are placed during averaging down.
 */

public class AddStepRuleConfig {

  public AddStepType type;

  public Double drop_pct_step;

  public ReferencePrice reference;
}
