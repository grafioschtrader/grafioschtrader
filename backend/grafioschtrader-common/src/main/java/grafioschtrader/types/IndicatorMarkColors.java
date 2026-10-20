package grafioschtrader.types;

/** Persisted indicator column presentation contract. */
public enum IndicatorMarkColors {
  LOW_NEGATIVE_HIGH_POSITIVE((byte) 0), LOW_POSITIVE_HIGH_NEGATIVE((byte) 1), NEUTRAL((byte) 2);

  private final byte value;

  IndicatorMarkColors(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return value;
  }

  public static IndicatorMarkColors getByValue(byte value) {
    for (var item : values())
      if (item.value == value)
        return item;
    throw new IllegalArgumentException("Unknown IndicatorMarkColors value: " + value);
  }
}
