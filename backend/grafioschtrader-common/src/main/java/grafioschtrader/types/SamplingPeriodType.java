package grafioschtrader.types;

/**
 * Enum for a sampling period
 *
 * Must have the same values like TimePeriodType
 */
public enum SamplingPeriodType {
  // Daily returns
  DAILY_RETURNS((byte) 0),
  // Monthly returns
  MONTHLY_RETURNS((byte) 1),
  // Annual returns
  ANNUAL_RETURNS((byte) 2);

  private final Byte value;

  private SamplingPeriodType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static SamplingPeriodType getSamplingPeriodTypeByValue(byte value) {
    for (SamplingPeriodType samplingPeriodType : SamplingPeriodType.values()) {
      if (samplingPeriodType.getValue() == value) {
        return samplingPeriodType;
      }
    }
    return null;
  }
}
