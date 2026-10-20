package grafioschtrader.types;

/** Persisted indicator column presentation contract. */
public enum IndicatorResultType {
  NUMBER((byte) 0), BOOLEAN((byte) 1);

  private final byte value;

  IndicatorResultType(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return value;
  }

  public static IndicatorResultType getByValue(byte value) {
    for (var item : values())
      if (item.value == value)
        return item;
    throw new IllegalArgumentException("Unknown IndicatorResultType value: " + value);
  }
}
