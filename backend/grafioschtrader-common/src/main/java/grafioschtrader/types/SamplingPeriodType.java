package grafioschtrader.types;

public enum SamplingPeriodType {
  // Must have the same values like TimePeriodType
  DAILY_RETURNS((byte) 0), MONTHLY_RETURNS((byte) 1), ANNUAL_RETURNS((byte) 2);

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
