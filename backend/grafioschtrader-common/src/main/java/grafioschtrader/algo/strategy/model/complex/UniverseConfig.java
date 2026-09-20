package grafioschtrader.algo.strategy.model.complex;

import java.util.List;

import grafioschtrader.algo.strategy.model.complex.enums.Direction;
import grafioschtrader.algo.strategy.model.complex.enums.UniverseMode;

/**
 * Defines the asset universe and allowed trade direction for the strategy.
 */

public class UniverseConfig {

  public UniverseMode mode;

  public List<String> assets;

  public Direction direction;
}
