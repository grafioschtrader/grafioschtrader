package grafioschtrader.algo.strategy.model;

public enum TimePeriod {
  TP_YEAR((byte) 1), TP_SEMI_ANNUAL((byte) 2), TP_QUARTER((byte) 4), TP_MONTH((byte) 12), TP_WEEK((byte) 52),
  TP_DAY((byte) 365);

  private final Byte value;

  private TimePeriod(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static TimePeriod getTimePeriod(byte value) {
    for (TimePeriod timePeriod : TimePeriod.values()) {
      if (timePeriod.getValue() == value) {
        return timePeriod;
      }
    }
    return null;
  }

}
