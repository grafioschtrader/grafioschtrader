package grafioschtrader.algo.strategy.model.complex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafioschtrader.algo.strategy.model.complex.enums.PriceField;

/**
 * Data source configuration: which price field and timeframe the strategy operates on.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataConfig {

  public PriceField price_field;

  public String timeframe;
}
