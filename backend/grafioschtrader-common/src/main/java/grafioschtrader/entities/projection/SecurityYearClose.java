package grafioschtrader.entities.projection;

import java.time.LocalDate;

/**
 * Projection interface representing annual summary data for a security, including the year-end close price, total
 * dividends for the year, and an optional currency pair close for conversion.
 */
public interface SecurityYearClose {

  /**
   * The date of the year-end record (typically December 31 of the year).
   *
   * @return the year-end date
   */
  LocalDate getDate();

  /**
   * The security’s closing price on the year-end date.
   *
   * @return the end-of-day close price for the security
   */
  double getSecurityClose();

  /**
   * The total sum of dividends paid by the security during the year.
   *
   * @return the annual dividend amount
   */
  double getYearDiv();

  /**
   * The closing price of the associated currency pair on the year-end date, used for currency conversion of the
   * security close and dividends.
   *
   * @return the currency pair’s close price
   */
  double getCurrencyClose();
}
