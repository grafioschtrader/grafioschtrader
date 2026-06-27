package grafioschtrader.entities.projection;

import java.time.LocalDate;

/**
 * Projection interface representing the period-end (month-end) close price of a security together with the sum of
 * dividends paid within that month and an optional currency-pair close for conversion.
 *
 * <p>
 * This is the month-granular counterpart to {@link SecurityYearClose}. The seasonality report fetches one row per
 * calendar month (the last available trading day of the month) and aggregates these rows into monthly, quarterly and
 * annual returns.
 * </p>
 */
public interface SecurityPeriodClose {

  /**
   * The date of the month-end record (the last available trading day of that calendar month).
   *
   * @return the month-end date
   */
  LocalDate getDate();

  /**
   * The security's closing price on the month-end date.
   *
   * @return the end-of-day close price for the security
   */
  double getSecurityClose();

  /**
   * The total sum of dividends with an ex-date within the same calendar month.
   *
   * @return the monthly dividend amount, zero when no dividend was paid
   */
  double getPeriodDiv();

  /**
   * The closing price of the associated currency pair on the month-end date, used to convert the security close and
   * dividends into the tenant's main currency. Only populated by the currency-conversion query variant.
   *
   * @return the currency pair's close price
   */
  double getCurrencyClose();
}
