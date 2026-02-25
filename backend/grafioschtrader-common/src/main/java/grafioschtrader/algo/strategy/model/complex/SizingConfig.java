package grafioschtrader.algo.strategy.model.complex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafioschtrader.algo.strategy.model.complex.enums.SizingMode;

/**
 * Reusable position sizing block. Used by entry configuration and averaging-down configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SizingConfig {

  public SizingMode mode;

  public Double pct;

  public Double amount;
}
