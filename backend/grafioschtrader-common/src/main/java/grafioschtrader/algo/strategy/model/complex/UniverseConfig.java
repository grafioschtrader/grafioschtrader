package grafioschtrader.algo.strategy.model.complex;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafioschtrader.algo.strategy.model.complex.enums.Direction;
import grafioschtrader.algo.strategy.model.complex.enums.UniverseMode;

/**
 * Defines the asset universe and allowed trade direction for the strategy.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UniverseConfig {

  public UniverseMode mode;

  public List<String> assets;

  public Direction direction;
}
