package grafioschtrader.reports;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import grafiosch.common.DateHelper;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.GlobalConstants;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionCurrenyGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;

/**
 * Base class for generating reports that group security positions and cash accounts by currency, with optional
 * sub-grouping by security account. Provides core functionality for currency-based aggregation and exchange rate
 * calculations used in portfolio reporting.
 * 
 * <p>
 * Supports two grouping strategies:
 * </p>
 * <ul>
 * <li><strong>Simple Currency Grouping:</strong> Groups all security positions by their currency</li>
 * <li><strong>Currency + Security Account Grouping:</strong> Groups positions by both currency and security account for
 * portfolios with multiple accounts per currency</li>
 * </ul>
 * 
 * <h3>Key Features:</h3>
 * <ul>
 * <li>Automatic multi-currency conversion to main currency using appropriate exchange rates</li>
 * <li>Historical exchange rate support for past dates, current rates for recent dates</li>
 * <li>Trading day validation with fallback logic for non-trading days</li>
 * <li>Currency-specific decimal precision for accurate calculations</li>
 * </ul>
 * 
 * <h3>Exchange Rate Handling:</h3>
 * <ul>
 * <li>Main currency positions: rate of 1.0 (no conversion)</li>
 * <li>Current/future dates: latest available rate from currency pair data</li>
 * <li>Historical dates: end-of-day rates from specified date</li>
 * <li>Missing rates: fallback to latest rate if no trading days between target date and yesterday</li>
 * <li>Throws {@code DataViolationException} for missing rates on trading days</li>
 * </ul>
 * 
 * <p>
 * This base class is extended by specific report implementations. Subclasses call the grouping methods to obtain
 * aggregated position summaries organized by currency. The class is thread-safe with lazy-loaded repository
 * dependencies.
 * </p>
 */
public class SecurityCashaccountGroupByCurrencyBaseReport {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  protected TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  protected CurrencypairJpaRepository currencypairJpaRepository;

  protected SecurityJpaRepository securityJpaRepository;

  protected Map<String, Integer> currencyPrecisionMap;

  @Autowired
  public void setSecurityJpaRepository(@Lazy final SecurityJpaRepository securityJpaRepository) {
    this.securityJpaRepository = securityJpaRepository;
  }

  @Autowired
  public void setCurrencypairJpaRepository(@Lazy final CurrencypairJpaRepository currencypairJpaRepository) {
    this.currencypairJpaRepository = currencypairJpaRepository;
  }

  public SecurityCashaccountGroupByCurrencyBaseReport(TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository,
      Map<String, Integer> currencyPrecisionMap) {
    this.tradingDaysPlusJpaRepository = tradingDaysPlusJpaRepository;
    this.currencyPrecisionMap = currencyPrecisionMap;
  }

  /**
   * Creates and calculates subtotals for security positions grouped by currency only. This is a convenience method that
   * delegates to the more comprehensive grouping method.
   * 
   * @param historyquoteJpaRepository   repository for historical quote data
   * @param securityPositionSummaryList list of security positions to group and calculate
   * @param dateCurrencyMap             currency and date mapping context for exchange rate calculations
   * @return map of currency codes to their aggregated position summaries
   */
  public Map<String, SecurityPositionCurrenyGroupSummary> createAndCalcSubtotalsPerCurrency(
      final HistoryquoteJpaRepository historyquoteJpaRepository,
      final List<SecurityPositionSummary> securityPositionSummaryList,
      final DateTransactionCurrencypairMap dateCurrencyMap) {
    return this.createAndCalcSubtotalsPerCurrencyAndIdSecurityaccount(historyquoteJpaRepository,
        securityPositionSummaryList, dateCurrencyMap, Collections.emptySet(), currencyPrecisionMap).currencyTotalMap;
  }

  /**
   * Creates and calculates subtotals for security positions grouped by currency and optionally by security account.
   * This method performs currency conversion, loads necessary historical quotes, and aggregates positions according to
   * the specified grouping criteria.
   * 
   * @param historyquoteJpaRepository          repository for historical quote data access
   * @param securityPositionSummaryList        list of security positions to process and group
   * @param dateCurrencyMap                    currency and date mapping context containing exchange rate information
   * @param seperateSecurityaccountCurrencySet set defining which currency/account combinations should be grouped
   *                                           separately
   * @param currencyPrecisionMap               map of currency codes to their decimal precision for calculations
   * @return result object containing both currency-only and currency/account groupings
   */
  public CurrencySecurityaccountCurrenyResult createAndCalcSubtotalsPerCurrencyAndIdSecurityaccount(
      final HistoryquoteJpaRepository historyquoteJpaRepository,
      final List<SecurityPositionSummary> securityPositionSummaryList,
      final DateTransactionCurrencypairMap dateCurrencyMap,
      final Set<SeperateSecurityaccountCurrency> seperateSecurityaccountCurrencySet,
      final Map<String, Integer> currencyPrecisionMap) {

    final SeperateSecurityaccountCurrency seperateSecurityaccountCurrency = new SeperateSecurityaccountCurrency();

    final CurrencySecurityaccountCurrenyResult cscr = new CurrencySecurityaccountCurrenyResult();

    ReportHelper.loadUntilDateHistoryquotes(historyquoteJpaRepository, dateCurrencyMap);

    for (final SecurityPositionSummary securityPositionSummary : securityPositionSummaryList) {
      final String currency = securityPositionSummary.securitycurrency.getCurrency();

      final double currencyExchangeRate = getCurrencyExChangeRate(currency, dateCurrencyMap);

      SecurityPositionCurrenyGroupSummary securityPositionCurrenyGroupSummary = null;
      seperateSecurityaccountCurrency.currency = currency;
      seperateSecurityaccountCurrency.idSecuritycashAccount = securityPositionSummary.usedIdSecurityaccount;
      seperateSecurityaccountCurrencySet.contains(seperateSecurityaccountCurrency);
      securityPositionCurrenyGroupSummary = cscr.securityaccountCurrencyTotalMap.get(seperateSecurityaccountCurrency);
      if (securityPositionCurrenyGroupSummary == null
          && seperateSecurityaccountCurrencySet.contains(seperateSecurityaccountCurrency)) {
        securityPositionCurrenyGroupSummary = new SecurityPositionCurrenyGroupSummary(currency, currencyExchangeRate,
            currencyPrecisionMap.getOrDefault(currency, GlobalConstants.FID_STANDARD_FRACTION_DIGITS));
        cscr.securityaccountCurrencyTotalMap.put(
            new SeperateSecurityaccountCurrency(currency, seperateSecurityaccountCurrency.idSecuritycashAccount),
            securityPositionCurrenyGroupSummary);
      } else {
        securityPositionCurrenyGroupSummary = cscr.currencyTotalMap.computeIfAbsent(currency,
            c -> new SecurityPositionCurrenyGroupSummary(c, currencyExchangeRate,
                currencyPrecisionMap.getOrDefault(currency, GlobalConstants.FID_STANDARD_FRACTION_DIGITS)));
      }
      securityPositionSummary.calcMainCurrency(currencyExchangeRate);
      securityPositionCurrenyGroupSummary.addToGroupSummaryAndCalcGroupTotals(securityPositionSummary);
    }

    return cscr;
  }

  /**
   * Determines the appropriate exchange rate for converting a currency to the main currency. Handles various scenarios
   * including historical dates, current dates, and missing rate fallbacks.
   * 
   * @param currency        the source currency to convert from
   * @param dateCurrencyMap currency and date context containing rate information
   * @return exchange rate from the source currency to main currency
   * @throws DataViolationException if required exchange rate is missing on a trading day
   */
  private Double getCurrencyExChangeRate(String currency, final DateTransactionCurrencypairMap dateCurrencyMap) {
    Double currencyExchangeRate = null;
    if (currency.equals(dateCurrencyMap.getMainCurrency())) {
      currencyExchangeRate = 1.0;
    } else {
      if (dateCurrencyMap.isUntilDateEqualNowOrAfterOrInActualWeekend()) {
        currencyExchangeRate = dateCurrencyMap.getCurrencypairByFromCurrency(currency).getSLast();
      } else {
        currencyExchangeRate = dateCurrencyMap.getExactDateAndFromCurrency(dateCurrencyMap.getUntilDate(), currency);
        if (currencyExchangeRate == null) {
          // Not found a EOD price for this date
          boolean hasTradingDaysBetweenUntilDateAndYesterday = tradingDaysPlusJpaRepository
              .hasTradingDayBetweenUntilYesterday(DateHelper.getLocalDate(dateCurrencyMap.getUntilDate()));

          if (hasTradingDaysBetweenUntilDateAndYesterday) {
            log.warn("Currencypair {}/{} for Date {} ist not updated!", currency, dateCurrencyMap.getMainCurrency(),
                dateCurrencyMap.getUntilDate());
            throw new DataViolationException("currencypair", "gt.missing.currencypair.day",
                new Object[] { dateCurrencyMap.getUntilDate(), currency, dateCurrencyMap.getMainCurrency() });
          } else {
            currencyExchangeRate = dateCurrencyMap.getCurrencypairByFromCurrency(currency).getSLast();
          }
        }
      }
    }
    return currencyExchangeRate;
  }

}

class CurrencySecurityaccountCurrenyResult {
  public Map<String, SecurityPositionCurrenyGroupSummary> currencyTotalMap = new HashMap<>();
  public Map<SeperateSecurityaccountCurrency, SecurityPositionCurrenyGroupSummary> securityaccountCurrencyTotalMap = new HashMap<>();
}

/**
 * Key class representing a combination of currency and security cash account for grouping purposes. Used in portfolios
 * that contain multiple security accounts per currency to enable separate aggregation.
 */
class SeperateSecurityaccountCurrency {
  /** The currency code for this grouping key */
  public String currency;
  /** The security cash account ID for this grouping key */
  public Integer idSecuritycashAccount;

  public SeperateSecurityaccountCurrency() {
  }

  public SeperateSecurityaccountCurrency(final String currency, final Integer idSecuritycashAccount) {
    super();
    this.currency = currency;
    this.idSecuritycashAccount = idSecuritycashAccount;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + currency.hashCode();
    result = prime * result + idSecuritycashAccount.hashCode();
    return result;
  }

  @Override
  public boolean equals(final Object obj) {
    final SeperateSecurityaccountCurrency other = (SeperateSecurityaccountCurrency) obj;
    return currency.equals(other.currency) && other.idSecuritycashAccount.equals(idSecuritycashAccount);
  }

}