package grafioschtrader.algo.strategy.model.alerts;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Alert when a certain position in a portfolio gains or loses a certain percentage and/or crosses an absolute price
 * threshold. At least one of (gainPercentage, losePercentage, upperValue, lowerValue) must be non-null.
 */
public class HoldingGainLosePercentAlert {
  @Min(value = 1)
  @Max(value = 500)
  Integer gainPercentage;

  @Min(value = 1)
  @Max(value = 500)
  Integer losePercentage;

  @Min(value = 0)
  Double upperValue;

  @Min(value = 0)
  Double lowerValue;

  public Integer getGainPercentage() {
    return gainPercentage;
  }

  public Integer getLosePercentage() {
    return losePercentage;
  }

  public Double getUpperValue() {
    return upperValue;
  }

  public Double getLowerValue() {
    return lowerValue;
  }
}
