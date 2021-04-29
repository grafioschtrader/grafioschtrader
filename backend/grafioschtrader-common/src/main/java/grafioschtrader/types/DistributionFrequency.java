package grafioschtrader.types;

public enum DistributionFrequency {
  // Never pay out
  DF_NONE((byte) 0),
  // One a year
  DF_YEAR((byte) 1),
  // Twice a year
  DF_SEMI_ANNUAL((byte) 2),
  // Each quarter
  DF_QUARTERLY((byte) 4),
  // Each Month
  DF_MONTHLY((byte) 12),
  // Not determined disbursement
  DF_AD_HOC((byte) 99);

  private final Byte value;

  private DistributionFrequency(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static DistributionFrequency getDistributionFrequency(byte value) {
    for (DistributionFrequency distributionFrequency : DistributionFrequency.values()) {
      if (distributionFrequency.getValue() == value) {
        return distributionFrequency;
      }
    }
    return null;
  }

  public Byte getDistributionFrequencyAsNumberWhenFrequency() {
    return DF_NONE.getValue().equals(value) || DF_AD_HOC.getValue().equals(value) ? null : value;
  }

}
