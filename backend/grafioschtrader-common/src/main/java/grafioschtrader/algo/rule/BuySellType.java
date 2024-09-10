package grafioschtrader.algo.rule;

public enum BuySellType {
  BS_BUY((byte) 1), BS_SELL((byte) 2);

  private final Byte value;

  private BuySellType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static BuySellType getBuySellType(byte value) {
    for (BuySellType buySell : BuySellType.values()) {
      if (buySell.getValue() == value) {
        return buySell;
      }
    }
    return null;
  }
}
