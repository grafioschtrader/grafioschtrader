package grafioschtrader.types;

/**
 * How often the instance fills a user-defined security field with the fill source lookup or language model by itself.
 * Without a period a language model field is asked only when the user starts a run, and a lookup field follows the
 * global setting {@code gt.udf.fill.resolver.refresh.days}. The value is the period in months and is saved in the
 * database; it must not be changed.
 */
public enum UDFFillPeriod {

  /** Fills empty cells automatically, including those of securities that enter the scope later; never refreshes. */
  FILL_PERIOD_ONCE((byte) 0),
  /** As once, and refreshes a value older than one month. */
  FILL_PERIOD_MONTHLY((byte) 1),
  /** As once, and refreshes a value older than three months. */
  FILL_PERIOD_QUARTERLY((byte) 3),
  /** As once, and refreshes a value older than six months. */
  FILL_PERIOD_HALF_YEARLY((byte) 6),
  /** As once, and refreshes a value older than twelve months. */
  FILL_PERIOD_YEARLY((byte) 12);

  private final Byte value;

  private UDFFillPeriod(final Byte value) {
    this.value = value;
  }

  /** The period in months, 0 for once. */
  public Byte getValue() {
    return this.value;
  }

  /** True when a value is looked up or asked for again after the period. */
  public boolean refreshes() {
    return value > 0;
  }

  public static UDFFillPeriod getByValue(byte value) {
    for (UDFFillPeriod period : UDFFillPeriod.values()) {
      if (period.getValue() == value) {
        return period;
      }
    }
    return null;
  }
}
