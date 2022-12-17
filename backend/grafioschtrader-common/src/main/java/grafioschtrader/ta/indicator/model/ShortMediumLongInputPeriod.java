package grafioschtrader.ta.indicator.model;

import jakarta.validation.constraints.Max;

public class ShortMediumLongInputPeriod {

  @Max(value = 999)
  public Integer taShortPeriod;

  @Max(value = 999)
  public Integer taMediumPeriod;

  @Max(value = 999)
  public Integer taLongPeriod;

  public ShortMediumLongInputPeriod() {

  }

  public ShortMediumLongInputPeriod(@Max(999) Integer taShortPeriod, @Max(999) Integer taMediumPeriod,
      @Max(999) Integer taLongPeriod) {
    this.taShortPeriod = taShortPeriod;
    this.taMediumPeriod = taMediumPeriod;
    this.taLongPeriod = taLongPeriod;
  }

}
