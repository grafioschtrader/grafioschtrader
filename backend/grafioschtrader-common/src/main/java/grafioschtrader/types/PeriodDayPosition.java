package grafioschtrader.types;

/**
 * Determines which day within a month/year period a standing order executes on. Only relevant when the repeat unit
 * is MONTHS or YEARS; ignored for DAYS-based intervals.
 */
public enum PeriodDayPosition {

  /** Use the explicit {@code dayOfExecution} value (1-28). */
  SPECIFIC_DAY((byte) 0),

  /** Execute on the first day of the period month. */
  FIRST_DAY((byte) 1),

  /** Execute on the last day of the period month. */
  LAST_DAY((byte) 2);

  private final Byte value;

  private PeriodDayPosition(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static PeriodDayPosition getByValue(byte value) {
    for (PeriodDayPosition pos : PeriodDayPosition.values()) {
      if (pos.getValue() == value) {
        return pos;
      }
    }
    return null;
  }
}
