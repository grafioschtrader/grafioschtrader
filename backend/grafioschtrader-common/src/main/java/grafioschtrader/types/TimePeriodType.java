package grafioschtrader.types;

public enum TimePeriodType {
  // Must have the same values like SamplingPeriodType
  DAILY((byte) 0), MONTHLY((byte) 1), ANNUAL((byte) 2);
  
  private final Byte value;
  
  private TimePeriodType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }
}
