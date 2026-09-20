package grafioschtrader.algo.strategy.model.complex;

import grafioschtrader.algo.strategy.model.complex.enums.PriceField;

/**
 * Data source configuration: which price field and timeframe the strategy operates on.
 */

public class DataConfig {

  public PriceField price_field;

  public String timeframe;
}
