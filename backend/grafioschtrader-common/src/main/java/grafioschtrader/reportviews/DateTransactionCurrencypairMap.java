package grafioschtrader.reportviews;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.exceptions.DataViolationException;

/**
 * Die instance is filled with the currency data. Reports which calculate in the
 * main currency use this class to access the history of currency conversion.
 *
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
   *
   * @param mainCurrency                               Main currency of the tenant
   *                                                   or porfolio
   * @param untilDate                                  Latest day which is
   *                                                   included in the report
   * @param dateTransactionCurrency                    Expect close price with
   *                                                   date for each day where a
   *                                                   transaction with the no
   *                                                   main currency is happened
   * @param currencypairs                              Used currency pairs by the
   *                                                   tenant or portfolio, get
   *                                                   the the last price but
   *                                                   history quote.
   * @param hasTradingDaysBetweenUntilDateAndYesterday
   * @param useUntilDateForFeeAndInterest
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
   * Search a target currency to the main currency for a certain date and returns
   * the exchange rate.
   *
   * @param date
   * @param fromCurrency
   * @return
   */
  public Double getPriceByDateAndFromCurrency(Date date, String fromCurrency, boolean requried) {

    Double closePrice = getExactDateAndFromCurrency(date, fromCurrency);
    if (closePrice == null) {
      if (closePrice == null && requried) {
        log.warn("Missing close price for date {} and currency pair from {} to {}", searchDateCurrency.date,
            fromCurrency, mainCurrency);
        throw new DataViolationException("currencypair", "gt.missing.currencypair.day",
            new Object[] { searchDateCurrency.date, fromCurrency, mainCurrency });
      }
    }

    return closePrice;
  }

  public Double getExactDateAndFromCurrency(Date date, String fromCurrency) {
    // searchDateCurrency.date = DateHelper.setTimeToZeroAndAddDay(date, 0);
    searchDateCurrency.date = date;
    searchDateCurrency.fromCurrency = fromCurrency;
    Double closePrice = dateFromCurrencyMap.get(searchDateCurrency);
    if (closePrice == null && (!hasTradingDaysBetweenUntilDateAndYesterday
        && DateHelper.getDateDiff(date, untilDate, TimeUnit.DAYS) <= 1)) {
      closePrice = getClosePriceFromLastPrice(date, fromCurrency);
    }

    return closePrice;
  }

  /**
   * Sometimes prices near the actual date are missing, because there were no
   * recently updates. In this case take the actual price from currency pair.
   *
   * @param fromCurrency
   * @return
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

class DateCurrency {
  public Date date;
  public String fromCurrency;

  public DateCurrency() {

  }

  public DateCurrency(Date date, String fromCurrency) {
    super();
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
