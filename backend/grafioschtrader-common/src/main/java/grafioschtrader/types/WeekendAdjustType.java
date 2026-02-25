package grafioschtrader.types;

/**
 * Controls how a standing order execution date is shifted when it falls on a weekend (Saturday or Sunday).
 */
public enum WeekendAdjustType {

  /** Shift the execution date to the preceding Friday. */
  BEFORE((byte) 0),

  /** Shift the execution date to the following Monday. */
  AFTER((byte) 1);

  private final Byte value;

  private WeekendAdjustType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static WeekendAdjustType getByValue(byte value) {
    for (WeekendAdjustType type : WeekendAdjustType.values()) {
      if (type.getValue() == value) {
        return type;
      }
    }
    return null;
  }
}
