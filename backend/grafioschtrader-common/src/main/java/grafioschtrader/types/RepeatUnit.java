package grafioschtrader.types;

/**
 * Defines the time unit for standing order repeat intervals. Combined with {@code repeatInterval}, this determines
 * how frequently a standing order generates transactions (e.g. every 20 days, monthly, quarterly, yearly).
 */
public enum RepeatUnit {

  /** Repeat every N days. Day-of-execution and period-day-position are ignored. */
  DAYS((byte) 0),

  /** Repeat every N months. Supports day-of-execution (1-28) or first/last day positioning. */
  MONTHS((byte) 1),

  /** Repeat every N years. Supports month-of-execution (1-12) and day-of-execution or first/last day positioning. */
  YEARS((byte) 2);

  private final Byte value;

  private RepeatUnit(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static RepeatUnit getByValue(byte value) {
    for (RepeatUnit unit : RepeatUnit.values()) {
      if (unit.getValue() == value) {
        return unit;
      }
    }
    return null;
  }
}
