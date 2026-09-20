package grafioschtrader.algo.strategy.model.complex.downside;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Shared parameter block for indicator and statistical rules. Fields are optional depending on the rule type:
 * {@code length} is used by period-based indicators (RSI, SMA), {@code lookback} by statistical rules (z-score).
 */

public class IndicatorParams {

  @Min(1)
  public Integer length;

  @Size(max = 10)
  public String condition;

  public Double value;

  @Min(1)
  public Integer lookback;
}
