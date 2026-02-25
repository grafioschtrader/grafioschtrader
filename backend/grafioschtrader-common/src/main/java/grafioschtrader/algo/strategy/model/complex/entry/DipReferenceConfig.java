package grafioschtrader.algo.strategy.model.complex.entry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafioschtrader.algo.strategy.model.complex.enums.DipReferenceType;

/**
 * Defines how a price dip is calculated relative to a reference point.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DipReferenceConfig {

  public DipReferenceType type;

  public Integer period;

  public String indicator;
}
