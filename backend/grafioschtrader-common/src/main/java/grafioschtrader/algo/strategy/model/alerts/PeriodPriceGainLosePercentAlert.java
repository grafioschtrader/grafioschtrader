package grafioschtrader.algo.strategy.model.alerts;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Alert when a security gains or loses a certain percentage in a certain days period. Restricted to the security level
 * only.
 */
public class PeriodPriceGainLosePercentAlert {

  @Min(value = 1)
  @Max(value = 999)
  Integer daysInPeriod;

  @Min(value = 1)
  @Max(value = 500)
  Integer gainPercentage;

  @Min(value = 1)
  @Max(value = 500)
  Integer losePercentage;

  public Integer getDaysInPeriod() {
    return daysInPeriod;
  }

  public void setDaysInPeriod(Integer daysInPeriod) {
    this.daysInPeriod = daysInPeriod;
  }

  public Integer getGainPercentage() {
    return gainPercentage;
  }

  public void setGainPercentage(Integer gainPercentage) {
    this.gainPercentage = gainPercentage;
  }

  public Integer getLosePercentage() {
    return losePercentage;
  }

  public void setLosePercentage(Integer losePercentage) {
    this.losePercentage = losePercentage;
  }
}
