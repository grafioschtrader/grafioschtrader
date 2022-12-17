package grafioschtrader.algo.strategy.model.alerts;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Alert when a security gain or lose a certain percentage in a certain days
 * period.</br>
 * Only securities of users watch list on top and asset class level are
 * supervised.</br>
 *
 * @author Hugo Graf
 *
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
}
