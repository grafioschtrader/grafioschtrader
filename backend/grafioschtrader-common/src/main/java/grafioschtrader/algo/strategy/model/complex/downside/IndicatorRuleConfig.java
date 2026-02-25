package grafioschtrader.algo.strategy.model.complex.downside;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafioschtrader.algo.strategy.model.complex.enums.IndicatorType;
import jakarta.validation.Valid;

/**
 * A single indicator-based rule used in downside decision making (e.g. RSI below threshold).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndicatorRuleConfig {

  public String id;

  public IndicatorType type;

  @Valid
  public IndicatorParams params;
}
