package grafioschtrader.types;

/**
 * Whether the series of a parent's history source is adjusted for one recorded spin-off. GT stores the parent's
 * history unadjusted for every recorded spin-off, so an adjusted series is multiplied back by the factor of the
 * spin-off before it is saved; an undetermined one is saved as delivered.
 */
public enum SpinOffHistoryBasis {
  /** Not yet decided; quotes before the action date are stored as delivered. */
  UNDETERMINED((byte) 0),
  /** The source delivers the parent's quotes before the action date unadjusted for this spin-off. */
  UNADJUSTED((byte) 1),
  /** The source divided the parent's quotes before the action date by the history factor of this spin-off. */
  ADJUSTED((byte) 2);

  private final byte value;

  SpinOffHistoryBasis(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return value;
  }

  public static SpinOffHistoryBasis getByValue(byte value) {
    for (SpinOffHistoryBasis entry : values()) {
      if (entry.value == value) {
        return entry;
      }
    }
    throw new IllegalArgumentException("Unknown SpinOffHistoryBasis: " + value);
  }
}
