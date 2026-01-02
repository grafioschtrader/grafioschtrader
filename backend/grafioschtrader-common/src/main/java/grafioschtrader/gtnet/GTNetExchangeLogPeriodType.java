package grafioschtrader.gtnet;

/**
 * Period types for GTNet exchange log aggregation. Defines the granularity of log entries
 * from individual requests to yearly aggregates.
 */
public enum GTNetExchangeLogPeriodType {
  INDIVIDUAL((byte) 0),
  DAILY((byte) 1),
  WEEKLY((byte) 2),
  MONTHLY((byte) 3),
  YEARLY((byte) 4);

  private final byte value;

  private GTNetExchangeLogPeriodType(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return this.value;
  }

  public static GTNetExchangeLogPeriodType getGTNetExchangeLogPeriodType(byte value) {
    for (GTNetExchangeLogPeriodType periodType : GTNetExchangeLogPeriodType.values()) {
      if (periodType.getValue() == value) {
        return periodType;
      }
    }
    return INDIVIDUAL;
  }
}
