package grafioschtrader.algo.strategy.model.complex;

import grafioschtrader.algo.strategy.model.complex.enums.SizingMode;

/**
 * Reusable position sizing block. Used by entry configuration and averaging-down configuration.
 */

public class SizingConfig {

  public SizingMode mode;

  public Double pct;

  public Double amount;
}
