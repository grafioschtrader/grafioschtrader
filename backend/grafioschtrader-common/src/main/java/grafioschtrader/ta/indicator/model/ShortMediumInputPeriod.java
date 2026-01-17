package grafioschtrader.ta.indicator.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Input model for technical indicators that use two periods (short and medium).
 * <p>
 * Used for oscillator indicators like RSI where typically fewer periods are needed
 * compared to moving averages (SMA/EMA).
 * </p>
 * <p>
 * The short period is required, while the medium period is optional. If the medium period
 * is null, only one indicator line will be calculated and displayed.
 * </p>
 */
public class ShortMediumInputPeriod {

  /**
   * The primary (short) calculation period. This is required.
   */
  @Max(value = 999)
  @Min(value = 2)
  @NotNull
  public Integer taShortPeriod;

  /**
   * The secondary (medium) calculation period. This is optional.
   * If null, only one indicator line will be calculated.
   */
  @Max(value = 999)
  @Min(value = 2)
  public Integer taMediumPeriod;

  public ShortMediumInputPeriod() {
  }

  public ShortMediumInputPeriod(@NotNull @Max(999) @Min(2) Integer taShortPeriod,
      @Max(999) @Min(2) Integer taMediumPeriod) {
    this.taShortPeriod = taShortPeriod;
    this.taMediumPeriod = taMediumPeriod;
  }

}
