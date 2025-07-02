package grafioschtrader.reportviews.performance;

/**
 * Enumeration defining the status of trading days for performance analysis and reporting.
 * 
 * <p>
 * This enum categorizes different types of days within performance analysis periods, allowing the system to distinguish
 * between normal trading days, holidays, and days with data quality issues. Each enum constant is associated with a
 * numeric value for efficient storage and database persistence.
 * </p>
 * 
 * <p>
 * The classification is essential for:
 * </p>
 * <ul>
 * <li>Performance calculation accuracy by excluding inappropriate days</li>
 * <li>UI visualization of different day types in calendars and charts</li>
 * <li>Data quality reporting and validation processes</li>
 * <li>Trading day validation in period selection interfaces</li>
 * </ul>
 */
public enum HolidayMissing {
  /**
   * Default state indicating no special classification.
   * 
   * <p>
   * Represents an unclassified or neutral state, typically used as a default value when the day type has not been
   * determined or when the classification is not applicable to the current context.
   * </p>
   */
  HM_NONE((byte) 0),

  /**
   * Normal trading day with complete data available.
   * 
   * <p>
   * Indicates a standard business day where markets were open, trading occurred, and complete end-of-day data is
   * available for performance calculations. These days form the foundation for accurate performance analysis.
   * </p>
   */
  HM_TRADING_DAY((byte) 1), 
  
  /**
   * Market holiday or non-trading day.
   * 
   * <p>
   * Represents days when markets were officially closed due to holidays,
   * weekends, or other scheduled non-trading periods. These days are excluded
   * from performance calculations as no trading activity occurred.
   * </p>
   */
  HM_HOLIDAY((byte) 2), 
  
  /**
   * Trading day with missing or incomplete historical data.
   * 
   * <p>
   * Indicates a day when markets were open for trading, but historical price
   * data is missing, incomplete, or unreliable. These days are excluded from
   * performance analysis to prevent calculation errors and ensure data quality.
   * Common causes include data provider issues, system outages, or delayed
   * data processing.
   * </p>
   */
  HM_HISTORY_DATA_MISSING((byte) 3);

  private final Byte value;

  private HolidayMissing(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static HolidayMissing getHolidayMissing(byte value) {
    for (HolidayMissing holidayMissing : HolidayMissing.values()) {
      if (holidayMissing.getValue() == value) {
        return holidayMissing;
      }
    }
    return null;
  }
}
