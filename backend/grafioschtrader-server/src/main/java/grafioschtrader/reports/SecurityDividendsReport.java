package grafioschtrader.reports;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Securitysplit.SplitFactorAfterBefore;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securitydividends.CashAccountPosition;
import grafioschtrader.reportviews.securitydividends.SecurityDividendsGrandTotal;
import grafioschtrader.reportviews.securitydividends.SecurityDividendsPosition;
import grafioschtrader.reportviews.securitydividends.SecurityDividendsYearGroup;
import grafioschtrader.reportviews.securitydividends.UnitsCounter;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TransactionType;

/**
 * Creates the objects for a "report" which is grouped by year. It includes all transaction. The main purpose is the sum
 * off dividends and interest by year.
 *
 * Attention: This class should not have a member variable, because there exist only one instance.
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
  private GlobalparametersService globalparametersService;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  public SecurityDividendsGrandTotal getSecurityDividendsGrandTotalByTenant(final Integer idTenant,
      final List<Integer> idsSecurityaccount, final List<Integer> idsCashaccount) {

    final Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);
    SecurityDividendsGrandTotal securityDividendsGrandTotal = new SecurityDividendsGrandTotal(tenant.getCurrency(),
        globalparametersService.getCurrencyPrecision());

    final DateTransactionCurrencypairMap dateCurrencyMap = getHistoryquoteAndCurrencypairs(tenant);
    final Map<Integer, Cashaccount> cashAccountsMap = getCashAccountMap(tenant, idsCashaccount);
    List<Transaction> transactions = getTransactions(cashAccountsMap.values(), idsSecurityaccount);

    final CompletableFuture<SecurityDividendsGrandTotal> cfSecurityDividendsGrandTotal = CompletableFuture
        .supplyAsync(() -> collectDataForPosition(tenant, cashAccountsMap, transactions, securityDividendsGrandTotal,
            dateCurrencyMap));
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

  private SecurityDividendsGrandTotal collectDataForPosition(final Tenant tenant,
      Map<Integer, Cashaccount> cashAccountsMap, List<Transaction> transactions,
      final SecurityDividendsGrandTotal securityDividendsGrandTotal,
      final DateTransactionCurrencypairMap dateCurrencyMap) {

    final CompletableFuture<Map<Integer, List<Securitysplit>>> cfSecuritysplitMap = CompletableFuture
        .supplyAsync(() -> securitysplitJpaRepository.getSecuritysplitMapByIdTenant(tenant.getIdTenant()));
    collectPositionByTransactions(cashAccountsMap, dateCurrencyMap, cfSecuritysplitMap.join(), transactions,
        securityDividendsGrandTotal);

    return securityDividendsGrandTotal;
  }

  private Map<Integer, Cashaccount> getCashAccountMap(Tenant tenant, final List<Integer> idsCashaccount) {
    boolean allCashaccounts = idsCashaccount.size() == 1 && idsCashaccount.get(0) == -1 ? true : false;
    Map<Integer, Cashaccount> cashAccountMap = new HashMap<>();
    tenant.getPortfolioList().forEach(portfolio -> portfolio.getCashaccountList().forEach(cashaccount -> {
      if (allCashaccounts || idsCashaccount.contains(cashaccount.getIdSecuritycashAccount())) {
        cashAccountMap.put(cashaccount.getIdSecuritycashAccount(), cashaccount);
      }
    }));
    return cashAccountMap;
  }

  /**
   * Collects all transactions from the given cash accounts that either have no security-account ID or whose
   * security-account ID is in the provided list, and whose type is at most FINANCE_COST. The results are sorted in
   * ascending order by transaction date.
   *
   * @param cashAccounts       the cash accounts to scan (must not be null)
   * @param idsSecurityaccount the security-account IDs to include (if empty, all IDs are allowed)
   * @return a list of matching transactions, sorted by date (oldest first)
   */
  private List<Transaction> getTransactions(Collection<Cashaccount> cashAccounts,
      final List<Integer> idsSecurityaccount) {
    List<Transaction> transactions = new ArrayList<>();

    cashAccounts.forEach(cashaccount -> {
      cashaccount.getTransactionList().stream()
          .filter(transaction -> transaction.getIdSecurityaccount() == null || idsSecurityaccount.size() == 0
              || idsSecurityaccount.contains(transaction.getIdSecurityaccount()))
          .filter(transaction -> transaction.getTransactionType().getValue() <= TransactionType.FINANCE_COST.getValue())
          .forEach(transactions::add);
    });

    Collections.sort(transactions);
    return transactions;
  }

  private SecurityDividendsGrandTotal collectPositionByTransactions(Map<Integer, Cashaccount> cashAccountsMap,
      final DateTransactionCurrencypairMap dateCurrencyMap, final Map<Integer, List<Securitysplit>> securitysplitMap,
      final List<Transaction> transactions, final SecurityDividendsGrandTotal securityDividendsGrandTotal) {

    Security security = null;

    int year = 0;
    int yearChangeWatcher = 0;

    final Map<Integer, UnitsCounter> unitsCounterBySecurityMap = new HashMap<>();
    final Map<Integer, Double> cashAccountsAmountMap = new HashMap<>();

    final Calendar calendar = new GregorianCalendar();

    SecurityDividendsYearGroup securityDividendsYearGroup = null;
    for (final Transaction transaction : transactions) {
      calendar.setTime(transaction.getTransactionTime());
      year = calendar.get(Calendar.YEAR);
      if (yearChangeWatcher != year) {
        fillEmptyAccountYears(cashAccountsMap, securityDividendsGrandTotal, yearChangeWatcher, year,
            cashAccountsAmountMap);
        // For some securities, there may have been no transactions in a given year.
        // Nevertheless, these open positions must be listed for that year.
        if (yearChangeWatcher != 0) {
          for (; yearChangeWatcher < year; yearChangeWatcher++) {
            createFillYearWithOpenPositions(securityDividendsGrandTotal, yearChangeWatcher, unitsCounterBySecurityMap,
                securitysplitMap);
          }
          transferCashaccountAmountToNewYear(securityDividendsYearGroup, cashAccountsAmountMap, cashAccountsMap);
        }
        yearChangeWatcher = year;
        securityDividendsYearGroup = securityDividendsGrandTotal.getOrCreateGroup(year);
      }

      Double amount = cashAccountsAmountMap.computeIfAbsent(transaction.getCashaccount().getIdSecuritycashAccount(),
          k -> Double.valueOf(0.0));
      amount += transaction.getCashaccountAmount();
      cashAccountsAmountMap.put(transaction.getCashaccount().getIdSecuritycashAccount(), amount);

      if (transaction.getSecurity() != null) {
        dateCurrencyMap.setUntilDate(new GregorianCalendar(year, 11, 31).getTime());
        calcSecurityTransaction(dateCurrencyMap, securityDividendsYearGroup, transaction, security, securitysplitMap,
            unitsCounterBySecurityMap);
      } else {
        calcAccountTransactions(dateCurrencyMap, securityDividendsYearGroup, transaction);
      }
    }
    if (year != 0) {
      createFillYearWithOpenPositions(securityDividendsGrandTotal, year, unitsCounterBySecurityMap, securitysplitMap);
      transferCashaccountAmountToNewYear(securityDividendsYearGroup, cashAccountsAmountMap, cashAccountsMap);
    }

    return securityDividendsGrandTotal;
  }

  /**
   * This is necessary if no transaction has been carried out in a year. The cash accounts must always appear annually.
   */
  private void fillEmptyAccountYears(Map<Integer, Cashaccount> cashAccountsMap,
      final SecurityDividendsGrandTotal securityDividendsGrandTotal, int yearChangeWatcher, int year,
      final Map<Integer, Double> cashAccountsAmountMap) {
    if (yearChangeWatcher != 0 && year - yearChangeWatcher > 1) {
      for (int i = yearChangeWatcher; i < year; i++) {
        SecurityDividendsYearGroup securityDividendsYearGroup = securityDividendsGrandTotal.getOrCreateGroup(i);
        transferCashaccountAmountToNewYear(securityDividendsYearGroup, cashAccountsAmountMap, cashAccountsMap);
      }
    }
  }

  private void transferCashaccountAmountToNewYear(SecurityDividendsYearGroup securityDividendsYearGroup,
      final Map<Integer, Double> cashAccountsAmountMap, Map<Integer, Cashaccount> cashAccountsMap) {
    for (var entry : cashAccountsAmountMap.entrySet()) {
      Cashaccount cashaccount = cashAccountsMap.get(entry.getKey());
      CashAccountPosition cashAccountPosition = securityDividendsYearGroup
          .getOrCreateAccountDividendPosition(cashaccount);
      cashAccountPosition.cashBalance = entry.getValue();
      securityDividendsYearGroup.yearInterestMC += cashAccountPosition.getRealReceivedDivInterestMC();
      securityDividendsYearGroup.yearFeeMC += cashAccountPosition.getFeeCashAccountMC()
          + cashAccountPosition.getFeeSecurityAccountMC();
    }
  }

  private void calcSecurityTransaction(DateTransactionCurrencypairMap dateCurrencyMap,
      SecurityDividendsYearGroup securityDividendsYearGroup, Transaction transaction, Security security,
      final Map<Integer, List<Securitysplit>> securitysplitMap, Map<Integer, UnitsCounter> unitsCounterBySecurityMap) {
    if (!transaction.getSecurity().equals(security)) {
      security = transaction.getSecurity();
    }
    final SecurityDividendsPosition securityDividendsPosition = securityDividendsYearGroup
        .getOrCreateSecurityDividendsPosition(transaction.getSecurity(),
            securitysplitMap.get(security.getIdSecuritycurrency()));

    final SplitFactorAfterBefore splitFactorAfterBefore = Securitysplit.calcSplitFatorForFromDateAndToDate(
        security.getIdSecuritycurrency(), transaction.getTransactionTime(), dateCurrencyMap.getUntilDate(),
        securitysplitMap);
    // Calculates the split factor until the end of the year for this transaction.
    // This allows the units to be calculated at the end of the year.
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
        log.error(
            "It is not possible to have dividends/interest in the year {} for security {} ISIN {} as no units are available.",
            securityDividendsYearGroup.year, transaction.getSecurity().getName(), transaction.getSecurity().getIsin());
      }
      securityDividendsPosition.updateDividendPosition(transaction, dateCurrencyMap);
      break;
    default:
      break;
    }
  }

  private void calcAccountTransactions(DateTransactionCurrencypairMap dateCurrencyMap,
      SecurityDividendsYearGroup securityDividendsYearGroup, Transaction transaction) {
    CashAccountPosition cashAccountPosition = securityDividendsYearGroup
        .getOrCreateAccountDividendPosition(transaction.getCashaccount());
    if (transaction.getTransactionType() == TransactionType.INTEREST_CASHACCOUNT) {
      cashAccountPosition.updateInterestPosition(transaction, securityDividendsYearGroup, dateCurrencyMap);
    } else if (transaction.getTransactionType() == TransactionType.FEE) {
      cashAccountPosition.updateFeePosition(transaction, securityDividendsYearGroup, dateCurrencyMap);
    }
  }

  /**
   * Calculate grand totals
   *
   */
  private void createGrandTotal(final Integer idTenant, final SecurityDividendsGrandTotal securityDividendsGrandTotal,
      final Map<Integer, Map<Integer, Historyquote>> historyquoteYearIdMap,
      final DateTransactionCurrencypairMap dateCurrencyMap) {
    securityDividendsGrandTotal.attachHistoryquoteAndCalcPositionTotal(historyquoteYearIdMap, dateCurrencyMap);
    securityDividendsGrandTotal.calcDivInterest();
  }

  /**
   * There may be no transaction for a given year, or there may be no transaction on an open position in a given year.
   * Nevertheless, this year must contain all open positions.
   *
   */
  private void createFillYearWithOpenPositions(final SecurityDividendsGrandTotal securityDividendsGrandTotal,
      final Integer year, final Map<Integer, UnitsCounter> unitsCounterBySecurityMap,
      final Map<Integer, List<Securitysplit>> securitysplitMap) {
    final SecurityDividendsYearGroup securityDividendsYearGroupLast = securityDividendsGrandTotal
        .getOrCreateGroup(year);
    securityDividendsYearGroupLast.fillYearWithOpenPositions(unitsCounterBySecurityMap, securitysplitMap);
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

  /**
   * Retrieves end-of-year history quotes for foreign currencies and securities for a tenant, grouped first by year and
   * then by security-currency ID.
   *
   * @param idTenant the tenant ID
   * @return a map where each key is a year and each value is a map of security-currency ID to its year-end history
   *         close price
   */
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
