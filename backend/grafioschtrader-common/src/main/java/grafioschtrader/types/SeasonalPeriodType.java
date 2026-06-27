package grafioschtrader.types;

/**
 * Granularity of a seasonality heat map: the matrix columns are either the twelve calendar months or the four
 * quarters of a year. The enum constant names are used directly as the request parameter value and as the frontend
 * select options.
 */
public enum SeasonalPeriodType {
  /** Twelve columns, January through December. */
  MONTHLY(12),
  /** Four columns, Q1 through Q4. */
  QUARTERLY(4);

  private final int numberOfColumns;

  private SeasonalPeriodType(final int numberOfColumns) {
    this.numberOfColumns = numberOfColumns;
  }

  /**
   * Number of period columns this granularity produces (12 for monthly, 4 for quarterly).
   *
   * @return the number of columns
   */
  public int getNumberOfColumns() {
    return numberOfColumns;
  }
}
