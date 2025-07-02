package grafioschtrader.reportviews.performance;

/**
 * Enumeration defining aggregation periods for performance analysis and reporting.
 * 
 * <p>
 * This enum specifies the time granularity used for organizing and presenting performance data within analysis windows.
 * It determines how individual trading days are grouped and aggregated for meaningful performance comparisons.
 * </p>
 */
public enum WeekYear {
  /**
   * Weekly aggregation period for short-term performance analysis.
   * 
   * <p>
   * Represents a weekly time frame where performance data is organized into individual trading days within a single
   * week. Typically results in 5 period steps representing Monday through Friday trading days.
   */
  WM_WEEK((byte) 0),

  /**
   * Yearly aggregation period for long-term performance analysis.
   * 
   * <p>
   * Represents a yearly time frame where performance data is organized into monthly summaries within a single year.
   * Typically results in 12 period steps representing January through December.
   * </p>
   */
  WM_YEAR((byte) 1);

  private final Byte value;

  private WeekYear(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static WeekYear getWeekMonth(byte value) {
    for (WeekYear weekMonth : WeekYear.values()) {
      if (weekMonth.getValue() == value) {
        return weekMonth;
      }
    }
    return null;
  }
}
