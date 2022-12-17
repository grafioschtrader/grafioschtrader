package grafioschtrader.algo.strategy.model.alerts;

import jakarta.validation.constraints.Min;

/**
 * Warn if a security exceeds a lower or upper threshold.<\br> Can only used on
 * the security level.
 *
 * @author Hugo Graf
 *
 */
public class AbsoluteValuePriceAlert {

  @Min(value = 0)
  Double lowerValue;

  @Min(value = 0)
  Double upperValue;
}
