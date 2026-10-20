package grafioschtrader.types;

/**
 * Outcome of a check that needs input outside the stored rows, such as the price or another field of the same answer.
 * Written by the load task; a check whose input is missing leaves {@link #NOT_CHECKED}, which is never read as passed.
 */
public enum FundamentalSourceCheck {
  NOT_CHECKED((byte) 0), PASSED((byte) 1), FAILED((byte) 2);

  private final byte value;

  FundamentalSourceCheck(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return value;
  }

  public static FundamentalSourceCheck getByValue(byte value) {
    for (var item : values())
      if (item.value == value)
        return item;
    throw new IllegalArgumentException("Unknown FundamentalSourceCheck value: " + value);
  }
}
