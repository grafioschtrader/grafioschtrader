package grafiosch.gtnet;

/**
 * Period types for GTNet exchange log aggregation.
 *
 * Defines the granularity of log entries from individual requests to yearly aggregates.
 * Used to configure how GTNet exchange logs are consolidated for storage and reporting.
 */
public enum GTNetExchangeLogPeriodType {
  /** Individual log entry per request/response */
  INDIVIDUAL((byte) 0),
  /** Daily aggregated log entries */
  DAILY((byte) 1),
  /** Weekly aggregated log entries */
  WEEKLY((byte) 2),
  /** Monthly aggregated log entries */
  MONTHLY((byte) 3),
  /** Yearly aggregated log entries */
  YEARLY((byte) 4);

  private final byte value;

  private GTNetExchangeLogPeriodType(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return this.value;
  }

  /**
   * Looks up a period type by its byte value.
   *
   * @param value the byte value to look up
   * @return the corresponding GTNetExchangeLogPeriodType, or INDIVIDUAL if not found
   */
  public static GTNetExchangeLogPeriodType getGTNetExchangeLogPeriodType(byte value) {
    for (GTNetExchangeLogPeriodType periodType : GTNetExchangeLogPeriodType.values()) {
      if (periodType.getValue() == value) {
        return periodType;
      }
    }
    return INDIVIDUAL;
  }
}
