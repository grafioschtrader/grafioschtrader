package grafioschtrader.types;

/** External source of a fundamental base value, also its column value in {@code security_fundamental}. */
public enum FundamentalSource {
  YAHOO((byte) 1), FINNHUB((byte) 2), EULERPOOL((byte) 3);

  private final byte value;

  FundamentalSource(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return value;
  }

  public static FundamentalSource getByValue(byte value) {
    for (var item : values())
      if (item.value == value)
        return item;
    throw new IllegalArgumentException("Unknown FundamentalSource value: " + value);
  }
}
