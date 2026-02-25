package grafioschtrader.algo.strategy.model.complex.profit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafioschtrader.algo.strategy.model.complex.enums.ReferencePrice;
import grafioschtrader.algo.strategy.model.complex.enums.TriggerType;

/**
 * Reusable trigger definition: specifies the type, threshold value, and price reference for triggering an event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TriggerConfig {

  public TriggerType type;

  public Double value;

  public ReferencePrice reference;
}
