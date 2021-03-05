package grafioschtrader.algo.rule;

public enum BuySell {
  BS_BUY((byte) 1), BS_SELL((byte) 2);

  private final Byte value;

  private BuySell(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static BuySell getBuySell(byte value) {
    for (BuySell buySell : BuySell.values()) {
      if (buySell.getValue() == value) {
        return buySell;
      }
    }
    return null;
  }
}
