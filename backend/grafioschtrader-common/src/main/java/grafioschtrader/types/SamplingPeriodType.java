package grafioschtrader.types;

public enum SamplingPeriodType {

  Daily((byte) 0), Monthly((byte) 1), Annual((byte) 2);

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
