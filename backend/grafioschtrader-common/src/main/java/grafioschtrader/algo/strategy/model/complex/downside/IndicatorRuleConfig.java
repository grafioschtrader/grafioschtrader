package grafioschtrader.algo.strategy.model.complex.downside;

import grafioschtrader.algo.strategy.model.complex.enums.IndicatorType;
import jakarta.validation.Valid;

/**
 * A single indicator-based rule used in downside decision making (e.g. RSI below threshold).
 */

public class IndicatorRuleConfig {

  public String id;

  public IndicatorType type;

  @Valid
  public IndicatorParams params;
}
