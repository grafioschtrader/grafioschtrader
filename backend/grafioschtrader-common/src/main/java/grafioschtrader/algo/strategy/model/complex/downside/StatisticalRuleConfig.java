package grafioschtrader.algo.strategy.model.complex.downside;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;

/**
 * A single statistical rule used in downside decision making (e.g. z-score below extreme threshold).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatisticalRuleConfig {

  public String id;

  public String type;

  @Valid
  public IndicatorParams params;
}
