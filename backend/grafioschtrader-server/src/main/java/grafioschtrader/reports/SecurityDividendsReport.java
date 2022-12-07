package grafioschtrader.reports;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Securitysplit.SplitFactorAfterBefore;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securitydividends.SecurityDividendsGrandTotal;
import grafioschtrader.reportviews.securitydividends.SecurityDividendsPosition;
import grafioschtrader.reportviews.securitydividends.SecurityDividendsYearGroup;
import grafioschtrader.reportviews.securitydividends.UnitsCounter;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.types.TransactionType;

/**
 * Creates the objects for a "report" which is grouped by year. It includes all
 * transaction but withdrawal and deposit. The main purpose is the sum off
 * dividends and interest by year.
 *
 *
 * Attention: This class shoud not have a member variable, because there exist
 * only one instance.
 *
 * @author Hugo Graf
 *
 */
@Component
public class SecurityDividendsReport {

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  public SecurityDividendsGrandTotal getSecurityDividendsGrandTotalByTenant(final Integer idTenant,
      final List<Integer> idsSecurityaccount, final List<Integer> idsCashaccount) {

    final Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);
    SecurityDividendsGrandTotal securityDividendsGrandTotal = new SecurityDividendsGrandTotal(tenant.getCurrency(),
        globalparametersJpaRepository.getCurrencyPrecision());

    final DateTransactionCurrencypairMap dateCurrencyMap = getHistoryquoteAndCurrencypairs(tenant);
    List<Transaction> transactions = getTransactions(tenant, idsSecurityaccount, idsCashaccount);

    final CompletableFuture<SecurityDividendsGrandTotal> cfSecurityDividendsGrandTotal = CompletableFuture
        .supplyAsync(() -> collectDataForPosition(tenant, transactions, securityDividendsGrandTotal, dateCurrencyMap));
    final CompletableFuture<Map<Integer, Map<Integer, Historyquote>>> cfHistoryquotes = CompletableFuture
        .supplyAsync(() -> getSecuritycurrencyHistoryEndOfYearsByIdTenant(idTenant));

    CompletableFuture.allOf(cfSecurityDividendsGrandTotal, cfHistoryquotes)
        .thenAccept(ignoredVoid -> createGrandTotal(idTenant, cfSecurityDividendsGrandTotal.join(),
            cfHistoryquotes.join(), dateCurrencyMap))
        .join();
    securityDividendsGrandTotal.portfolioList = tenant.getPortfolioList();
    return securityDividendsGrandTotal;
  }

  private DateTransactionCurrencypairMap getHistoryquoteAndCurrencypairs(final Tenant tenant) {
    final CompletableFuture<List<Object[]>> dateTransactionCurrencyFuture = CompletableFuture.supplyAsync(
        () -> historyquoteJpaRepository.getHistoryquoteCurrenciesForIntrFeeBuySellDivByIdTenantAndMainCurrency(
            tenant.getIdTenant(), tenant.getCurrency()));
    final CompletableFuture<List<Currencypair>> currencypairsFuture = CompletableFuture.supplyAsync(
        () -> currencypairJpaRepository.getAllCurrencypairsByTenantInPortfolioAndAccounts(tenant.getIdTenant()));

    return new DateTransactionCurrencypairMap(tenant.getCurrency(), null, dateTransactionCurrencyFuture.join(),
        currencypairsFuture.join(), true, false);
  }

  private SecurityDividendsGrandTotal collectDataForPosition(final Tenant tenant, List<Transaction> transactions,
      final SecurityDividendsGrandTotal securityDividendsGrandTotal,
      final DateTransactionCurrencypairMap dateCurrencyMap) {

    final CompletableFuture<Map<Integer, List<Securitysplit>>> cfSecuritysplitMap = CompletableFuture
        .supplyAsync(() -> securitysplitJpaRepository.getSecuritysplitMapByIdTenant(tenant.getIdTenant()));
    collectPositionByTransactions(dateCurrencyMap, cfSecuritysplitMap.join(), transactions,
        securityDividendsGrandTotal);

    return securityDividendsGrandTotal;
  }

  private List<Transaction> getTransactions(Tenant tenant, final List<Integer> idsSecurityaccount,
      final List<Integer> idsCashaccount) {
    List<Transaction> transactions = new ArrayList<>();

    boolean allCashaccounts = idsCashaccount.size() == 1 && idsCashaccount.get(0) == -1 ? true : false;

    tenant.getPortfolioList().forEach(portfolio -> portfolio.getCashaccountList().forEach(cashaccount -> {
      cashaccount.getTransactionList().stream()
          .filter(transaction -> transaction.getIdSecurityaccount() == null || idsSecurityaccount.size() == 0
              || idsSecurityaccount.contains(transaction.getIdSecurityaccount()))
          .filter(transaction -> transaction.getTransactionType().getValue() >= TransactionType.INTEREST_CASHACCOUNT
              .getValue() && transaction.getTransactionType().getValue() <= TransactionType.DIVIDEND.getValue())
          .filter(transaction -> allCashaccounts
              || (transaction.getTransactionType() != TransactionType.INTEREST_CASHACCOUNT
                  || transaction.getTransactionType() == TransactionType.INTEREST_CASHACCOUNT
                      && idsCashaccount.contains(transaction.getCashaccount().getIdSecuritycashAccount())))
          .filter(transaction -> allCashaccounts || ((transaction.getTransactionType() != TransactionType.FEE
              || transaction.getIdSecurityaccount() != null)
              || (transaction.getTransactionType() == TransactionType.FEE
                  && idsCashaccount.contains(transaction.getCashaccount().getIdSecuritycashAccount()))))
          .forEach(transactions::add);
    }));

    Collections.sort(transactions);
    return transactions;
  }

  private SecurityDividendsGrandTotal collectPositionByTransactions(
      final DateTransactionCurrencypairMap dateCurrencyMap, final Map<Integer, List<Securitysplit>> securitysplitMap,
      final List<Transaction> transactions, final SecurityDividendsGrandTotal securityDividendsGrandTotal) {

    Security security = null;

    int year = 0;
    int yearChangeWatcher = 0;

    final Map<Integer, UnitsCounter> unitsCounterBySecurityMap = new HashMap<>();

    final Calendar calendar = new GregorianCalendar();

    for (final Transaction transaction : transactions) {

      calendar.setTime(transaction.getTransactionTime());
      year = calendar.get(Calendar.YEAR);
      if (yearChangeWatcher != year) {
        // For some Security the maybe no transaction in a certain year, no we need to
        // add this securities
        if (yearChangeWatcher != 0) {
          for (; yearChangeWatcher < year; yearChangeWatcher++) {
            adjustUnits(securityDividendsGrandTotal, yearChangeWatcher, unitsCounterBySecurityMap);
          }
        }
        yearChangeWatcher = year;
      }

      final SecurityDividendsYearGroup securityDividendsYearGroup = securityDividendsGrandTotal.getOrCreateGroup(year);

      if (transaction.getSecurity() != null) {
        dateCurrencyMap.setUntilDate(new GregorianCalendar(year, 11, 31).getTime());

        calcSecurityTransaction(dateCurrencyMap, securityDividendsYearGroup, transaction, security, securitysplitMap,
            unitsCounterBySecurityMap);
      } else {
        calcInterestOrFee(dateCurrencyMap, securityDividendsYearGroup, transaction);
      }
    }
    if (year != 0) {
      adjustUnits(securityDividendsGrandTotal, year, unitsCounterBySecurityMap);
    }

    return securityDividendsGrandTotal;
  }

  private void calcSecurityTransaction(DateTransactionCurrencypairMap dateCurrencyMap,
      SecurityDividendsYearGroup securityDividendsYearGroup, Transaction transaction, Security security,
      final Map<Integer, List<Securitysplit>> securitysplitMap, Map<Integer, UnitsCounter> unitsCounterBySecurityMap) {
    if (!transaction.getSecurity().equals(security)) {
      security = transaction.getSecurity();
    }
    final SecurityDividendsPosition securityDividendsPosition = securityDividendsYearGroup
        .getOrCreateSecurityDividendsPosition(transaction.getSecurity());

    final SplitFactorAfterBefore splitFactorAfterBefore = Securitysplit.calcSplitFatorForFromDateAndToDate(
        security.getIdSecuritycurrency(), transaction.getTransactionTime(), dateCurrencyMap.getUntilDate(),
        securitysplitMap);
    final double unitsSplited = transaction.getUnits() * splitFactorAfterBefore.fromToDateFactor;

    switch (transaction.getTransactionType()) {
    case ACCUMULATE:
      createOrGetUnitsCounter(transaction.getSecurity(), unitsSplited, unitsCounterBySecurityMap);
      securityDividendsPosition.updateAccumulateReduce(transaction, securityDividendsYearGroup, dateCurrencyMap);
      break;
    case REDUCE:
      createOrGetUnitsCounter(transaction.getSecurity(), unitsSplited * -1, unitsCounterBySecurityMap);
      securityDividendsPosition.updateAccumulateReduce(transaction, securityDividendsYearGroup, dateCurrencyMap);
      break;

    case DIVIDEND:
      if (unitsCounterBySecurityMap.get(transaction.getSecurity().getIdSecuritycurrency()) == null) {
        System.err.println(transaction.getSecurity());
      }
      securityDividendsPosition.updateDividendPosition(transaction, dateCurrencyMap);
      break;
    default:
      break;
    }
  }

  private void calcInterestOrFee(DateTransactionCurrencypairMap dateCurrencyMap,
      SecurityDividendsYearGroup securityDividendsYearGroup, Transaction transaction) {
    if (transaction.getTransactionType() == TransactionType.INTEREST_CASHACCOUNT) {
      securityDividendsYearGroup.addInterest(transaction.getInterestExRate(dateCurrencyMap));
    } else if (transaction.getTransactionType() == TransactionType.FEE) {
      securityDividendsYearGroup.addFee(transaction.getFeeExRate(dateCurrencyMap));
    }
  }

  /**
   * Calculate grand totals
   *
   * @param idTenant
   * @param securityDividendsGrandTotal
   * @param historyquoteYearIdMap
   * @param dateCurrencyMap
   */
  private void createGrandTotal(final Integer idTenant, final SecurityDividendsGrandTotal securityDividendsGrandTotal,
      final Map<Integer, Map<Integer, Historyquote>> historyquoteYearIdMap,
      final DateTransactionCurrencypairMap dateCurrencyMap) {
    securityDividendsGrandTotal.attachHistoryquoteAndCalcPositionTotal(historyquoteYearIdMap, dateCurrencyMap);
    securityDividendsGrandTotal.calcDivInterest();
  }

  private void adjustUnits(final SecurityDividendsGrandTotal securityDividendsGrandTotal, final Integer year,
      final Map<Integer, UnitsCounter> unitsCounterBySecurityMap) {
    final SecurityDividendsYearGroup securityDividendsYearGroupLast = securityDividendsGrandTotal
        .getOrCreateGroup(year);
    securityDividendsYearGroupLast.adjustUnits(unitsCounterBySecurityMap);
  }

  private void createOrGetUnitsCounter(final Security security, final Double addRecudeUntis,
      final Map<Integer, UnitsCounter> unitsCounterBySecurityMap) {
    final UnitsCounter unitsCounter = unitsCounterBySecurityMap.get(security.getIdSecuritycurrency());
    if (unitsCounter == null) {
      unitsCounterBySecurityMap.put(security.getIdSecuritycurrency(), new UnitsCounter(security, addRecudeUntis));
    } else {
      unitsCounter.addUnits(addRecudeUntis);
    }
  }

  private Map<Integer, Map<Integer, Historyquote>> getSecuritycurrencyHistoryEndOfYearsByIdTenant(
      final Integer idTenant) {
    final Calendar calendar = new GregorianCalendar();
    final Function<Date, Integer> tranformDateToYear = (date) -> {
      calendar.setTime(date);
      return calendar.get(Calendar.YEAR);
    };

    return historyquoteJpaRepository.getSecuritycurrencyHistoryEndOfYearsByIdTenant(idTenant).stream()
        .collect(Collectors.groupingBy(historyquote -> tranformDateToYear.apply(historyquote.getDate()),
            Collectors.toMap(Historyquote::getIdSecuritycurrency, Function.identity())));
  }

}
