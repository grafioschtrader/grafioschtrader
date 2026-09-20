package grafioschtrader.service;

/**
 * What one observation of a value against a bound amounted to, compared with the previous observation of the same
 * bound.
 *
 * <p>
 * The distinction that matters is between {@link #BASELINE_ESTABLISHED} and the two crossings. A price that already
 * sits above its upper bound when the alert is created has not crossed anything; reporting it would tell the user
 * something they configured rather than something that happened. Only the transition is a crossing.
 * </p>
 */
public enum AlgoCrossingResult {

  /** First valid observation of this bound, or the first after a configuration change. No alarm. */
  BASELINE_ESTABLISHED,

  /** The value moved from at or below the bound to above it. */
  CROSSED_UP,

  /** The value moved from at or above the bound to below it. */
  CROSSED_DOWN,

  /** The value stayed on the side it was already on. No alarm. */
  NO_CHANGE;

  /**
   * Whether this result is a crossing that should be reported.
   *
   * @return true for the two crossings, false for a fresh baseline and for no change
   */
  public boolean isCrossing() {
    return this == CROSSED_UP || this == CROSSED_DOWN;
  }

  /**
   * The direction to record on the alarm, so that an upward and a downward crossing of the same alert on the same day
   * are two signals rather than one.
   *
   * @return 1 upward, -1 downward, 0 when this result is not a crossing
   */
  public byte direction() {
    return switch (this) {
    case CROSSED_UP -> (byte) 1;
    case CROSSED_DOWN -> (byte) -1;
    default -> (byte) 0;
    };
  }

}
