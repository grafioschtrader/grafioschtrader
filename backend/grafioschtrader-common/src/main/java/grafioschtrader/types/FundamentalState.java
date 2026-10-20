package grafioschtrader.types;

/**
 * Grade the selection gives a fundamental row. Per security and bound metric at most one row is {@link #CONFIRMED},
 * {@link #CHECKED} or {@link #SINGLE}, and that row binds the expression variable.
 */
public enum FundamentalState {
  /** Not selected */
  CANDIDATE((byte) 0),
  /** Agrees with a value of another source for a comparable period */
  CONFIRMED((byte) 1),
  /** Single value whose source check passed */
  CHECKED((byte) 2),
  /** Single value that could be neither confirmed nor checked; shown with this grade */
  SINGLE((byte) 3),
  /** Contradicted by another source or by the fiscal year against trailing twelve months rule; never shown */
  SUSPECT((byte) 4);

  private final byte value;

  FundamentalState(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return value;
  }

  public static FundamentalState getByValue(byte value) {
    for (var item : values())
      if (item.value == value)
        return item;
    throw new IllegalArgumentException("Unknown FundamentalState value: " + value);
  }
}
