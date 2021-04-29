package grafioschtrader.types;

public enum TradingPlatformFeePlan {

  // free - no pay
  FP_FREE((byte) 0),
  // Cost are always the same
  FP_FLAT((byte) 1),
  // Cost are paid for a share
  FP_PER_SHARE((byte) 2),
  // Percentage of the transaction value
  FP_PER_AMOUNT_PERCENTAGE((byte) 3),
  // Graduated rates of transaction value
  FP_PER_AMOUNT_GRADUATED((byte) 4),
  // Some products may have a flat fee when others are paid by the amount of the
  // transaction
  FP_FLAT_OR_PER_AMOUNT_PERCENTAGE((byte) 5),
  // Some products may have a flat fee when others have graduated price model
  FP_FLAT_OR_PER_AMOUNT_GRADUATED((byte) 6),
  // Mixed fee models
  FP_MIXED((byte) 7);

  private final Byte value;

  private TradingPlatformFeePlan(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static TradingPlatformFeePlan getTradingPlatformFeePlan(byte value) {
    for (TradingPlatformFeePlan tradingPlatformFeePlan : TradingPlatformFeePlan.values()) {
      if (tradingPlatformFeePlan.getValue() == value) {
        return tradingPlatformFeePlan;
      }
    }
    return null;
  }
}
