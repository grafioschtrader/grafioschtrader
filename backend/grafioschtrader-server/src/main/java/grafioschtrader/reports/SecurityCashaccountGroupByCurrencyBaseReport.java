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

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.exceptions.DataViolationException;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionCurrenyGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;

/**
 * It groups the calculated securities. This class supports two different
 * groupings, one groups the securities by currencies. The other groups them by
 * security account and currencies.
 *
 *
 * @author Hugo Graf
 *
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

  public Map<String, SecurityPositionCurrenyGroupSummary> createAndCalcSubtotalsPerCurrency(
      final HistoryquoteJpaRepository historyquoteJpaRepository,
      final List<SecurityPositionSummary> securityPositionSummaryList,
      final DateTransactionCurrencypairMap dateCurrencyMap) {
    return this.createAndCalcSubtotalsPerCurrencyAndIdSecurityaccount(historyquoteJpaRepository,
        securityPositionSummaryList, dateCurrencyMap, Collections.emptySet(), currencyPrecisionMap).currencyTotalMap;
  }

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
 * A portfolio may contain more than only one security account. It is possible
 * to connect a cash account to a certain security account.
 *
 * @author Hugo Graf
 *
 */
class SeperateSecurityaccountCurrency {
  public String currency;
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