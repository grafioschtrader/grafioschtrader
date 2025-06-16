package grafioschtrader.reportviews;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafiosch.common.DateHelper;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Currencypair;

/**
 * Central repository for currency exchange rate data used in financial reports that calculate values in a main
 * currency. Provides access to historical currency conversion rates and manages currency pair mappings for
 * multi-currency portfolio calculations.
 * 
 * <p>
 * This class serves as the primary interface for retrieving exchange rates needed to convert foreign currency
 * transactions and positions to a tenant's or portfolio's main currency. It maintains both historical end-of-day rates
 * and current market rates with intelligent fallback mechanisms for missing data.
 * </p>
 * 
 * <h3>Key Features:</h3>
 * <ul>
 * <li>Historical exchange rate lookup by date and currency pair</li>
 * <li>Intelligent fallback to current rates when historical data is missing</li>
 * <li>Trading day awareness with weekend and holiday handling</li>
 * <li>Multiple currency pair lookup methods (by currencies, by ID)</li>
 * <li>Configurable behavior for fee and interest calculations</li>
 * </ul>
 * 
 * <h3>Rate Resolution Strategy:</h3>
 * <ul>
 * <li>Primary: Historical end-of-day rate for the specific date</li>
 * <li>Fallback: Current market rate if historical data unavailable and within acceptable timeframe</li>
 * <li>Error: Throws exception if required rate missing on trading days</li>
 * </ul>
 * 
 * <p>
 * The class is designed for use in financial reporting where accurate currency conversion is critical for portfolio
 * valuation, performance calculations, and regulatory reporting.
 * </p>
 */
public class DateTransactionCurrencypairMap {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private DateCurrency searchDateCurrency = new DateCurrency();
  private String mainCurrency;
  private Date untilDate;
  private Map<DateCurrency, Double> dateFromCurrencyMap = new HashMap<>();

  private Map<FromToCurrencyWithDate, Double> fromToCurrencyWithDateMap = new HashMap<>();

  private Map<FromToCurrency, Currencypair> currencypairFromToCurrencyMap = new HashMap<>();
  private Map<Integer, Currencypair> currencypairIdCurrencypairMap = new HashMap<>();
  private boolean isUntilDateEqualNowOrAfter;
  private boolean untilDateDataLoaded;
  private boolean isUntilDateEqualNowOrAfterOrInActualWeekend;
  private boolean useUntilDateForFeeAndInterest = true;
  private boolean hasTradingDaysBetweenUntilDateAndYesterday = false;

  public DateTransactionCurrencypairMap(final Date untilDate, boolean hasTradingDaysBetweenUntilDateAndYesterday) {
    this.untilDate = untilDate;
    this.hasTradingDaysBetweenUntilDateAndYesterday = hasTradingDaysBetweenUntilDateAndYesterday;
  }

  /**
   * Creates a comprehensive currency map with exchange rate data and currency pair information. This is the primary
   * constructor used for multi-currency financial reporting.
   * 
   * @param mainCurrency                               the main currency of the tenant or portfolio (target currency for
   *                                                   conversions)
   * @param untilDate                                  the latest date included in the report period
   * @param dateTransactionCurrency                    historical exchange rate data as Object arrays containing [Date,
   *                                                   String, Double]
   * @param currencypairs                              list of currency pairs used by the tenant or portfolio
   * @param hasTradingDaysBetweenUntilDateAndYesterday whether trading days exist between until date and yesterday
   * @param useUntilDateForFeeAndInterest              whether to use until date for fee and interest rate calculations
   */
  public DateTransactionCurrencypairMap(final String mainCurrency, final Date untilDate,
      List<Object[]> dateTransactionCurrency, List<Currencypair> currencypairs,
      boolean hasTradingDaysBetweenUntilDateAndYesterday, boolean useUntilDateForFeeAndInterest) {
    this.mainCurrency = mainCurrency;
    this.untilDate = untilDate;
    this.hasTradingDaysBetweenUntilDateAndYesterday = hasTradingDaysBetweenUntilDateAndYesterday;
    this.useUntilDateForFeeAndInterest = useUntilDateForFeeAndInterest;
    isUntilDateEqualNowOrAfter = untilDate == null || DateHelper.isTodayOrAfter(untilDate);
    isUntilDateEqualNowOrAfterOrInActualWeekend = untilDate == null
        || DateHelper.isUntilDateEqualNowOrAfterOrInActualWeekend(untilDate);

    putToDateFromCurrencyMap(dateTransactionCurrency);
    currencypairs.forEach(currencypair -> {
      currencypairFromToCurrencyMap
          .put(new FromToCurrency(currencypair.getFromCurrency(), currencypair.getToCurrency()), currencypair);
      currencypairIdCurrencypairMap.put(currencypair.getIdSecuritycurrency(), currencypair);
    });

  }

  public DateTransactionCurrencypairMap(final String mainCurrency, final Date untilDate,
      List<Object[]> dateTransactionCurrency, List<Currencypair> currencypairs,
      boolean hasTradingDaysBetweenUntilDateAndYesterday) {
    this(mainCurrency, untilDate, dateTransactionCurrency, currencypairs, hasTradingDaysBetweenUntilDateAndYesterday,
        true);
  }

  /**
   * Populates the internal exchange rate maps from the provided historical data. Processes Object arrays containing
   * date, currency, and rate information.
   * 
   * @param dateCurrency list of Object arrays with [Date, String, Double] representing exchange rates
   */
  public void putToDateFromCurrencyMap(List<Object[]> dateCurrency) {
    dateCurrency.forEach(objects -> {
      dateFromCurrencyMap.put(new DateCurrency((Date) objects[0], (String) objects[1]), (Double) objects[2]);
      fromToCurrencyWithDateMap.put(
          new FromToCurrencyWithDate((String) objects[1], mainCurrency, ((java.sql.Date) objects[0]).toLocalDate()),
          (Double) objects[2]);
    });
  }

  public Map<FromToCurrencyWithDate, Double> getFromToCurrencyWithDateMap() {
    return fromToCurrencyWithDateMap;
  }

  public void untilDateDataIsLoaded() {
    this.untilDateDataLoaded = true;
  }

  public boolean isUntilDateDataLoaded() {
    return untilDateDataLoaded;
  }

  /**
   * Retrieves the exchange rate from a source currency to the main currency for a specific date. Uses intelligent
   * fallback logic when exact date data is not available.
   * 
   * @param date         the date for which to retrieve the exchange rate
   * @param fromCurrency the source currency to convert from
   * @param required     whether the rate is required (throws exception if missing and true)
   * @return exchange rate from source currency to main currency, or null if not found and not required
   * @throws DataViolationException if required rate is missing
   */
  public Double getPriceByDateAndFromCurrency(Date date, String fromCurrency, boolean required) {
    Double closePrice = getExactDateAndFromCurrency(date, fromCurrency);
    if (closePrice == null) {
      if (closePrice == null && required) {
        log.warn("Missing close price for date {} and currency pair from {} to {}", searchDateCurrency.date,
            fromCurrency, mainCurrency);
        throw new DataViolationException("currencypair", "gt.missing.currencypair.day",
            new Object[] { searchDateCurrency.date, fromCurrency, mainCurrency });
      }
    }
    return closePrice;
  }

  /**
   * Attempts to find the exact exchange rate for a specific date and currency. Falls back to current rates if
   * historical data is missing and conditions permit.
   * 
   * @param date         the date for which to find the exchange rate
   * @param fromCurrency the source currency
   * @return exchange rate if found, null otherwise
   */
  public Double getExactDateAndFromCurrency(Date date, String fromCurrency) {
    searchDateCurrency.date = date;
    searchDateCurrency.fromCurrency = fromCurrency;
    Double closePrice = dateFromCurrencyMap.get(searchDateCurrency);
    if (closePrice == null
        && (!hasTradingDaysBetweenUntilDateAndYesterday && DateHelper.getDateDiff(date, untilDate, TimeUnit.DAYS) <= 1
            || untilDate.after(new Date()))) {
      closePrice = getClosePriceFromLastPrice(date, fromCurrency);
    }

    return closePrice;
  }

  /**
   * Retrieves the current market rate when historical data is missing for recent dates. Only returns a rate if the date
   * is within an acceptable timeframe from now and no recent updates have occurred to provide historical data.
   * 
   * @param date         the date for which historical data was missing
   * @param fromCurrency the source currency
   * @return current market rate if within acceptable timeframe, null otherwise
   */
  private Double getClosePriceFromLastPrice(Date date, String fromCurrency) {
    Double closePrice = null;
    long diffDays = DateHelper.getDateDiff(date, new Date(), TimeUnit.DAYS);
    if (diffDays <= GlobalConstants.MAX_DAY_DIFF_CURRENCY_UNTIL_NOW) {
      Currencypair currencypair = getCurrencypairByFromCurrency(fromCurrency);
      if (currencypair != null) {
        closePrice = currencypair.getSLast();
      }
    }
    return closePrice;
  }

  public boolean isUntilDateEqualNowOrAfter() {
    return isUntilDateEqualNowOrAfter;
  }

  public boolean isUntilDateEqualNowOrAfterOrInActualWeekend() {
    return this.isUntilDateEqualNowOrAfterOrInActualWeekend;
  }

  public Date getUntilDate() {
    return untilDate;
  }

  public void setUntilDate(Date untilDate) {
    this.untilDate = untilDate;
  }

  public String getMainCurrency() {
    return mainCurrency;
  }

  public Currencypair getCurrencypairByFromCurrency(String fromCurrency) {
    FromToCurrency fromToCurrency = new FromToCurrency(fromCurrency, mainCurrency);
    return currencypairFromToCurrencyMap.get(fromToCurrency);
  }

  public Currencypair getCurrencypairByFromToCurrency(String fromCurrency, String toCurrency) {
    FromToCurrency fromToCurrency = new FromToCurrency(fromCurrency, toCurrency);
    return currencypairFromToCurrencyMap.get(fromToCurrency);
  }

  public Currencypair getCurrencypairByIdCurrencypair(Integer idSecuritycurrency) {
    return currencypairIdCurrencypairMap.get(idSecuritycurrency);
  }

  public boolean isUseUntilDateForFeeAndInterest() {
    return useUntilDateForFeeAndInterest;
  }

  public void setUseUntilDateForFeeAndInterest(boolean useUntilDateForFeeAndInterest) {
    this.useUntilDateForFeeAndInterest = useUntilDateForFeeAndInterest;
  }

  public Map<FromToCurrency, Currencypair> getCurrencypairFromToCurrencyMap() {
    return currencypairFromToCurrencyMap;
  }

}

/**
 * Internal key class for looking up exchange rates by date and source currency. Used as a composite key in the exchange
 * rate cache for efficient lookups.
 */
class DateCurrency {
  /** The date for the exchange rate lookup */
  public Date date;
  /** The source currency for the exchange rate lookup */
  public String fromCurrency;

  public DateCurrency() {

  }

  public DateCurrency(Date date, String fromCurrency) {
    this.date = date;
    this.fromCurrency = fromCurrency;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + date.hashCode();
    result = prime * result + fromCurrency.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    DateCurrency dateCurrency = (DateCurrency) obj;
    return this == obj || this.date.equals(dateCurrency.date) && this.fromCurrency.equals(dateCurrency.fromCurrency);
  }
}
