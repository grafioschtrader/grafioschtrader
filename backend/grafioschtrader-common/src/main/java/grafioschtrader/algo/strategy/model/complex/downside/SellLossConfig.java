package grafioschtrader.algo.strategy.model.complex.downside;

import grafioschtrader.algo.strategy.model.complex.enums.OrderType;
import grafioschtrader.algo.strategy.model.complex.enums.ReferencePrice;
import grafioschtrader.algo.strategy.model.complex.enums.StopType;

/**
 * Variant A of downside management: sell the position at a loss using a stop-loss mechanism.
 */

public class SellLossConfig {

  public Boolean enabled;

  public StopType stop_type;

  public ReferencePrice stop_reference;

  public Double stop_threshold_pct;

  public OrderType order_type;

  public String action;
}
