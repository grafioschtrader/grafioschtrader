package grafioschtrader.tax.germany;

import java.time.LocalDate;
import java.util.List;

import grafioschtrader.entities.Security;

/**
 * The market data the German tax calculation needs besides the transactions: closing exchange rates to euro, the
 * prices and distributions of a fund for its advance lump sum, and the base interest rate of a year. Every method
 * returns null for what is not known, so that the calculation can keep the gap visible instead of replacing it.
 */
public interface GermanTaxMarketData {

  /** A price of a security on a day, on the split basis of today. */
  record PricePoint(LocalDate date, double close) {
  }

  /** The distribution of a fund unit on a day, on the split basis of today and in its own currency. */
  record Distribution(LocalDate date, double amountPerUnit, String currency) {
  }

  /**
   * The closing exchange rate of a currency to euro on a day.
   *
   * @param currency the ISO code of the currency
   * @param date     the day
   * @return 1 for EUR, the closing rate, or null when none exists for that day
   */
  Double rateToEur(String currency, LocalDate date);

  /**
   * The first price of a year, which is only valid within seven calendar days after the 1 January.
   *
   * @param idSecuritycurrency the fund
   * @param year               the calendar year
   * @return the price or null when the history starts later
   */
  PricePoint firstPrice(Integer idSecuritycurrency, int year);

  /**
   * The last price of a year, which is only valid within seven calendar days before the 31 December.
   *
   * @param idSecuritycurrency the fund
   * @param year               the calendar year
   * @return the price or null when the history ends earlier
   */
  PricePoint lastPrice(Integer idSecuritycurrency, int year);

  /**
   * The distributions of a fund unit in a calendar year.
   *
   * @param security the fund
   * @param year     the calendar year
   * @return the distributions, an empty list when it is established that the fund paid none, and null when the
   *         distributions of the year are not known
   */
  List<Distribution> distributions(Security security, int year);

  /**
   * The base interest rate of a year for the advance lump sum.
   *
   * @param year the calendar year
   * @return the rate in percent, 2.53 for 2.53 %, or null when none is stored for the year
   */
  Double baseRate(int year);
}
