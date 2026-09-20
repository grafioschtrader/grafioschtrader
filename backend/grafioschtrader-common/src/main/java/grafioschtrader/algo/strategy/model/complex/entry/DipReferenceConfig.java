package grafioschtrader.algo.strategy.model.complex.entry;

import grafioschtrader.algo.strategy.model.complex.enums.DipReferenceType;

/**
 * Defines how a price dip is calculated relative to a reference point.
 */

public class DipReferenceConfig {

  public DipReferenceType type;

  public Integer period;

  public String indicator;
}
