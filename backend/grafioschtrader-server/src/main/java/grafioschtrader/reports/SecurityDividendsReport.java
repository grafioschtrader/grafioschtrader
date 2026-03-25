package grafioschtrader.reports;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
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
import grafioschtrader.reportviews.securitydividends.SecurityDividendsYearGroup.MarginTracker;
import grafioschtrader.reportviews.securitydividends.UnitsCounter;
import grafiosch.entities.TaxYear;
import grafioschtrader.entities.IctaxSecurityTaxData;
import grafioschtrader.entities.TaxSecurityYearConfig;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.IctaxSecurityTaxDataJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TaxSecurityYearConfigJpaRepository;
import grafioschtrader.repository.TaxYearJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TransactionType;

/**
 * Service component responsible for generating comprehensive dividend and interest reports grouped by year.
 * 
 * <p>
 * This report service aggregates all financial transactions for a tenant across specified security and cash accounts,
 * with a primary focus on calculating annual dividend and interest income summaries. The generated report provides
 * detailed breakdowns of investment income, fees, and portfolio valuations organized by calendar year.
 * </p>
 * 
 * <p>
 * The report organizes data hierarchically:
 * </p>
 * <ul>
 * <li><strong>Grand Total Level:</strong> Aggregated data across all years and accounts</li>
 * <li><strong>Year Group Level:</strong> Annual summaries for each calendar year</li>
 * <li><strong>Position Level:</strong> Individual security and cash account positions within each year</li>
 * </ul>
 * 
 * <p>
 * Key financial metrics include dividend income, interest income, transaction fees, portfolio valuations, and currency
 * conversions to the tenant's main currency. The service handles multi-currency transactions using historical exchange
 * rates for accurate conversions.
 * </p>
 * 
 * <p>
 * <strong>Important:</strong> This class is designed as a stateless Spring component and should not contain member
 * variables, as only one instance exists in the application context.
 * </p>
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

  @Autowired
  private IctaxSecurityTaxDataJpaRepository ictaxSecurityTaxDataJpaRepository;

  @Autowired
  private TaxSecurityYearConfigJpaRepository taxSecurityYearConfigJpaRepository;

  @Autowired
  private TaxYearJpaRepository taxYearJpaRepository;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  /**
   * Generates a comprehensive dividend and interest report for a tenant across specified accounts.
   * 
   * <p>
   * This method creates a complete financial report summarizing dividend income, interest earnings, fees, and portfolio
   * valuations organized by calendar year. The report processes all relevant transactions and applies currency
   * conversions to present data in the tenant's main currency.
   * </p>
   * 
   * <p>
   * Account Selection:
   * </p>
   * <ul>
   * <li>Pass Arrays.asList(-1) to include all security accounts or all cash accounts</li>
   * <li>Provide specific account IDs to limit the report scope</li>
   * </ul>
   * 
   * <p>
   * The report includes transactions of types: ACCUMULATE, REDUCE, DIVIDEND, INTEREST_CASHACCOUNT, FEE, and all
   * transactions with type value ≤ FINANCE_COST. Year-end valuations use the latest available historical quotes with
   * split adjustments applied.
   * </p>
   * 
   * @param idTenant           the unique identifier of the tenant for whom to generate the report
   * @param idsSecurityaccount list of security account IDs to include in the report. Pass Arrays.asList(-1) to include
   *                           all security accounts
   * @param idsCashaccount     list of cash account IDs to include in the report. Pass Arrays.asList(-1) to include all
   *                           cash accounts
   * 
   * @return a SecurityDividendsGrandTotal containing the complete dividend and interest report with annual breakdowns,
   *         position details, and grand totals across all years
   * 
   * @throws IllegalArgumentException if the tenant ID is null or invalid
   * @throws SecurityException        if the current user lacks access rights to the specified tenant
   */
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
    securityDividendsGrandTotal.tenantCountry = tenant.getCountry();
    securityDividendsGrandTotal.taxExportSettings = tenant.getTaxExportSettings();
    securityDividendsGrandTotal.availableTaxYears = taxYearJpaRepository.findAll().stream()
        .map(TaxYear::getTaxYear)
        .distinct()
        .sorted(Comparator.reverseOrder())
        .toList();
    return securityDividendsGrandTotal;
  }

  /**
   * Retrieves and prepares currency conversion data and historical quotes needed for the report.
   * 
   * <p>
   * This method concurrently loads currency pairs and historical quotes for transactions involving different currencies
   * than the tenant's main currency. The data is used for accurate currency conversions throughout the report
   * generation process.
   * </p>
   * 
   * @param tenant the tenant entity containing currency and portfolio information
   * @return a DateTransactionCurrencypairMap containing currency conversion data and historical quotes
   */
  private DateTransactionCurrencypairMap getHistoryquoteAndCurrencypairs(final Tenant tenant) {
    final CompletableFuture<List<Object[]>> dateTransactionCurrencyFuture = CompletableFuture.supplyAsync(
        () -> historyquoteJpaRepository.getHistoryquoteCurrenciesForIntrFeeBuySellDivByIdTenantAndMainCurrency(
            tenant.getIdTenant(), tenant.getCurrency()));
    final CompletableFuture<List<Currencypair>> currencypairsFuture = CompletableFuture.supplyAsync(
        () -> currencypairJpaRepository.getAllCurrencypairsByTenantInPortfolioAndAccounts(tenant.getIdTenant()));

    return new DateTransactionCurrencypairMap(tenant.getCurrency(), null, dateTransactionCurrencyFuture.join(),
        currencypairsFuture.join(), true, false);
  }

  /**
   * Processes transaction data and calculates position information for the dividend report.
   * 
   * <p>
   * This method handles the core transaction processing logic, including security split adjustments, currency
   * conversions, and position calculations. It processes transactions chronologically and groups them by year for the
   * report structure.
   * </p>
   * 
   * @param tenant                      the tenant entity
   * @param cashAccountsMap             map of cash accounts included in the report
   * @param transactions                list of transactions to process
   * @param securityDividendsGrandTotal the grand total object to populate
   * @param dateCurrencyMap             currency conversion data for multi-currency transactions
   * @return the updated SecurityDividendsGrandTotal with processed transaction data
   */
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

  /**
   * Creates a map of cash accounts based on the tenant's portfolios and specified account filter.
   * 
   * <p>
   * This method filters cash accounts from the tenant's portfolios based on the provided account IDs. If the list
   * contains only -1, all cash accounts are included. Otherwise, only accounts with matching IDs are included in the
   * map.
   * </p>
   * 
   * @param tenant         the tenant entity containing portfolio information
   * @param idsCashaccount list of cash account IDs to include, or Arrays.asList(-1) for all accounts
   * @return a map with cash account IDs as keys and Cashaccount entities as values
   */
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
          .filter(transaction -> transaction.getIdSecurityaccount() == null || idsSecurityaccount.isEmpty()
              || (idsSecurityaccount.size() == 1 && idsSecurityaccount.get(0) == -1)
              || idsSecurityaccount.contains(transaction.getIdSecurityaccount()))
          .filter(transaction -> transaction.getTransactionType().getValue() <= TransactionType.FINANCE_COST.getValue())
          .forEach(transactions::add);
    });

    Collections.sort(transactions);
    return transactions;
  }

  /**
   * Processes transactions chronologically and calculates position data for the dividend report.
   * 
   * <p>
   * This method iterates through transactions in chronological order, grouping them by calendar year and calculating
   * dividend, interest, and fee amounts. It handles year transitions, maintains running position totals, and applies
   * security split adjustments where necessary.
   * </p>
   * 
   * <p>
   * The method tracks cash account balances across years and creates year groups for securities with open positions
   * even when no transactions occurred in specific years.
   * </p>
   * 
   * @param cashAccountsMap             map of cash accounts included in the report
   * @param dateCurrencyMap             currency conversion data for multi-currency calculations
   * @param securitysplitMap            map of security splits for position adjustments
   * @param transactions                list of transactions sorted by date
   * @param securityDividendsGrandTotal the grand total object to populate with calculated data
   * @return the updated SecurityDividendsGrandTotal with processed position data
   */
  private SecurityDividendsGrandTotal collectPositionByTransactions(Map<Integer, Cashaccount> cashAccountsMap,
      final DateTransactionCurrencypairMap dateCurrencyMap, final Map<Integer, List<Securitysplit>> securitysplitMap,
      final List<Transaction> transactions, final SecurityDividendsGrandTotal securityDividendsGrandTotal) {
    Security security = null;
    int year = 0;
    int yearChangeWatcher = 0;
    final Map<Integer, UnitsCounter> unitsCounterBySecurityMap = new HashMap<>();
    final Map<Integer, Double> cashAccountsAmountMap = new HashMap<>();
    final Map<Integer, Map<Integer, MarginTracker>> marginOpenTransaction = new HashMap<>();

    SecurityDividendsYearGroup securityDividendsYearGroup = null;
    for (final Transaction transaction : transactions) {
      year = transaction.getTransactionTime().getYear();
      if (yearChangeWatcher != year) {
        fillEmptyAccountYears(cashAccountsMap, securityDividendsGrandTotal, yearChangeWatcher, year,
            cashAccountsAmountMap);
        // For some securities, there may have been no transactions in a given year.
        // Nevertheless, these open positions must be listed for that year.
        if (yearChangeWatcher != 0) {
          int startYear = yearChangeWatcher;
          for (; yearChangeWatcher < year; yearChangeWatcher++) {
            if (yearChangeWatcher > startYear) {
              adjustUnitsCounterForNewYear(yearChangeWatcher, unitsCounterBySecurityMap, securitysplitMap, marginOpenTransaction);
            }
            createFillYearWithOpenPositions(securityDividendsGrandTotal, yearChangeWatcher, unitsCounterBySecurityMap,
                securitysplitMap, marginOpenTransaction);
          }
          transferCashaccountAmountToNewYear(securityDividendsYearGroup, cashAccountsAmountMap, cashAccountsMap);
          adjustUnitsCounterForNewYear(year, unitsCounterBySecurityMap, securitysplitMap, marginOpenTransaction);
        }
        yearChangeWatcher = year;
        securityDividendsYearGroup = securityDividendsGrandTotal.getOrCreateGroup(year);
      }

      Double amount = cashAccountsAmountMap.computeIfAbsent(transaction.getCashaccount().getIdSecuritycashAccount(),
          k -> Double.valueOf(0.0));
      amount += transaction.getCashaccountAmount();
      cashAccountsAmountMap.put(transaction.getCashaccount().getIdSecuritycashAccount(), amount);
      if (transaction.getSecurity() != null) {
        dateCurrencyMap.setUntilDate(LocalDate.of(year, 12, 31));
        calcSecurityTransaction(dateCurrencyMap, marginOpenTransaction, securityDividendsYearGroup, transaction,
            security, securitysplitMap, unitsCounterBySecurityMap);
      } else {
        calcAccountTransactions(dateCurrencyMap, securityDividendsYearGroup, transaction);
      }
    }
    if (year != 0) {
      createFillYearWithOpenPositions(securityDividendsGrandTotal, year, unitsCounterBySecurityMap, securitysplitMap, marginOpenTransaction);
      transferCashaccountAmountToNewYear(securityDividendsYearGroup, cashAccountsAmountMap, cashAccountsMap);
    }
    return securityDividendsGrandTotal;
  }

  /**
   * Fills in missing year groups for cash accounts when no transactions occurred in certain years.
   * 
   * <p>
   * This method ensures that cash accounts appear in the report for every year between the first and last transaction
   * years, even when no transactions occurred. This provides continuity in the year-over-year analysis and maintains
   * accurate cash balance tracking.
   * </p>
   * 
   * @param cashAccountsMap             map of cash accounts to process
   * @param securityDividendsGrandTotal the grand total object to update
   * @param yearChangeWatcher           the previous year being processed
   * @param year                        the current year being processed
   * @param cashAccountsAmountMap       map tracking cash account balances
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

  /**
   * Transfers cash account balances and fee calculations to a new year group.
   * 
   * <p>
   * This method updates the year group with current cash account balances and calculates the total interest and fees
   * for the year. It ensures that cash balances are properly carried forward between years and that annual totals are
   * accurately maintained.
   * </p>
   * 
   * @param securityDividendsYearGroup the year group to update
   * @param cashAccountsAmountMap      map of current cash account balances
   * @param cashAccountsMap            map of cash account entities
   */
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

  /**
   * Processes a security transaction and updates the corresponding position data.
   * 
   * <p>
   * This method handles buy/sell transactions (ACCUMULATE/REDUCE) and dividend payments for securities. It applies
   * security split adjustments to maintain accurate unit counts and calculates currency conversions for multi-currency
   * portfolios. The method also validates that dividend payments only occur when units are held.
   * </p>
   * 
   * @param dateCurrencyMap            currency conversion data for the transaction
   * @param securityDividendsYearGroup the year group to update
   * @param transaction                the security transaction to process
   * @param security                   the security entity (may be null, will be set from transaction)
   * @param securitysplitMap           map of security splits for position adjustments
   * @param unitsCounterBySecurityMap  map tracking unit holdings by security
   */
  private void calcSecurityTransaction(DateTransactionCurrencypairMap dateCurrencyMap,
      Map<Integer, Map<Integer, MarginTracker>> marginOpenTransaction,
      SecurityDividendsYearGroup securityDividendsYearGroup, Transaction transaction, Security security,
      final Map<Integer, List<Securitysplit>> securitysplitMap, Map<Integer, UnitsCounter> unitsCounterBySecurityMap) {
    if (!transaction.getSecurity().equals(security)) {
      security = transaction.getSecurity();
    }
    
    
    final SecurityDividendsPosition securityDividendsPosition = securityDividendsYearGroup
        .getOrCreateSecurityDividendsPosition(transaction.getSecurity(),
            securitysplitMap.get(security.getIdSecuritycurrency()));

    final SplitFactorAfterBefore splitFactorAfterBefore = Securitysplit.calcSplitFatorForFromDateAndToDate(
        security.getIdSecuritycurrency(), transaction.getTransactionTime().toLocalDate(), dateCurrencyMap.getUntilDate(),
        securitysplitMap);
    // Calculates the split factor until the end of the year for this transaction.
    // This allows the units to be calculated at the end of the year.
    final double unitsSplited = transaction.getUnits() * splitFactorAfterBefore.fromToDateFactor;

    switch (transaction.getTransactionType()) {
    case ACCUMULATE:
      createOrGetUnitsCounter(transaction.getSecurity(), unitsSplited, unitsCounterBySecurityMap,
          transaction.getTransactionTime().toLocalDate());
      trackMarginSecurityUnits(marginOpenTransaction, transaction, securityDividendsPosition, unitsSplited);
      securityDividendsPosition.updateAccumulateReduce(transaction, securityDividendsYearGroup, dateCurrencyMap);
      break;
    case REDUCE:
      createOrGetUnitsCounter(transaction.getSecurity(), unitsSplited * -1, unitsCounterBySecurityMap,
          transaction.getTransactionTime().toLocalDate());
      trackMarginSecurityUnits(marginOpenTransaction, transaction, securityDividendsPosition, unitsSplited * -1);
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
    case FINANCE_COST:
      securityDividendsPosition.updateFinanceCost(transaction, securityDividendsYearGroup, dateCurrencyMap);
      if (transaction.getConnectedIdTransaction() != null) {
        Map<Integer, MarginTracker> securityMarginMap = marginOpenTransaction
            .get(transaction.getSecurity().getId());
        if (securityMarginMap != null) {
          MarginTracker mt = securityMarginMap.get(transaction.getConnectedIdTransaction());
          if (mt != null) {
            mt.lastFinanceCostDate = transaction.getTransactionDateAsLocalDate();
          }
        }
      }
      break;
    default:
      break;
    }
  }

  /**
   * Processes cash account transactions such as interest payments and fees.
   * 
   * <p>
   * This method handles transactions that affect cash accounts directly, including interest earned on cash deposits and
   * various fees charged to the account. It updates the appropriate cash account position within the year group and
   * applies currency conversions as needed.
   * </p>
   * 
   * @param dateCurrencyMap            currency conversion data for the transaction
   * @param securityDividendsYearGroup the year group to update
   * @param transaction                the cash account transaction to process
   */
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
   * Calculates and finalizes grand total values across all years in the report.
   * 
   * <p>
   * This method performs the final calculations for the dividend report, including attaching year-end historical quotes
   * for portfolio valuations and computing overall dividend and interest totals. It ensures all currency conversions
   * are applied and that the report totals are accurate and complete.
   * </p>
   * 
   * @param idTenant                    the tenant ID for the report
   * @param securityDividendsGrandTotal the grand total object to finalize
   * @param historyquoteYearIdMap       map of historical quotes by year for portfolio valuations
   * @param dateCurrencyMap             currency conversion data for final calculations
   */
  private void createGrandTotal(final Integer idTenant, final SecurityDividendsGrandTotal securityDividendsGrandTotal,
      final Map<Integer, Map<Integer, Historyquote>> historyquoteYearIdMap,
      final DateTransactionCurrencypairMap dateCurrencyMap) {
    securityDividendsGrandTotal.attachHistoryquoteAndCalcPositionTotal(historyquoteYearIdMap, dateCurrencyMap);
    securityDividendsGrandTotal.calcDivInterest();
    securityDividendsGrandTotal.hasMarginData = securityDividendsGrandTotal.getSecurityDividendsYearGroup().stream()
        .flatMap(yg -> yg.getSecurityDividendsPositions().stream())
        .anyMatch(p -> p.security.isMarginInstrument());
    enrichWithIctaxData(securityDividendsGrandTotal);
    markExcludedSecurities(idTenant, securityDividendsGrandTotal);
  }

  /**
   * Enriches the dividend report with ICTax Swiss tax data. For each year group, looks up matching tax data by ISIN and
   * attaches tax values and payment details to each security position.
   */
  private void enrichWithIctaxData(SecurityDividendsGrandTotal grandTotal) {
    for (SecurityDividendsYearGroup yearGroup : grandTotal.getSecurityDividendsYearGroup()) {
      enrichWithIctaxData(yearGroup, yearGroup.year.shortValue());
      if (yearGroup.getSecurityDividendsPositions().stream()
          .anyMatch(p -> p.ictaxPayments != null || p.ictaxTotalTaxValueChf != null)) {
        grandTotal.hasIctaxData = true;
      }
    }
  }

  /**
   * Enriches a specific year group with ICTax data for the given tax year. Looks up matching tax data by ISIN and
   * attaches tax values and per-payment details to each security position using the unit timeline for correct
   * per-payment unit counts.
   *
   * @param yearGroup the year group to enrich
   * @param taxYear   the tax year to look up ICTax data for
   */
  public void enrichWithIctaxData(SecurityDividendsYearGroup yearGroup, short taxYear) {
    Set<String> isins = yearGroup.getSecurityDividendsPositions().stream()
        .filter(p -> p.security.getIsin() != null && !p.security.getIsin().isEmpty())
        .map(p -> p.security.getIsin()).collect(Collectors.toSet());
    if (isins.isEmpty()) {
      return;
    }
    List<IctaxSecurityTaxData> taxDataList = ictaxSecurityTaxDataJpaRepository.findByIsinInAndTaxYear(isins, taxYear);
    if (taxDataList.isEmpty()) {
      yearGroup.getSecurityDividendsPositions().forEach(p -> {
        p.ictaxTaxValuePerUnitChf = null;
        p.ictaxTotalTaxValueChf = null;
        p.ictaxPayments = null;
        p.ictaxTotalPaymentValueChf = null;
      });
      return;
    }
    Map<String, IctaxSecurityTaxData> taxDataByIsin = taxDataList.stream()
        .collect(Collectors.toMap(IctaxSecurityTaxData::getIsin, Function.identity(), (a, b) -> a));

    yearGroup.yearIctaxTotalTaxValueChf = 0.0;
    yearGroup.yearIctaxTotalPaymentValueChf = 0.0;

    for (SecurityDividendsPosition position : yearGroup.getSecurityDividendsPositions()) {
      if (position.security.getIsin() == null) {
        continue;
      }
      IctaxSecurityTaxData taxData = taxDataByIsin.get(position.security.getIsin());
      if (taxData != null) {
        double ictaxMultiplier = calcIctaxMultiplier(position);
        position.ictaxTaxValuePerUnitChf = taxData.getTaxValueChf();
        if (taxData.getTaxValueChf() != null && position.unitsAtEndOfYear > 0) {
          position.ictaxTotalTaxValueChf = taxData.getTaxValueChf() * ictaxMultiplier;
          yearGroup.yearIctaxTotalTaxValueChf += position.ictaxTotalTaxValueChf;
        }
        position.ictaxPayments = taxData.getPayments();
        if (taxData.getPayments() != null) {
          double totalPayment = 0.0;
          for (var payment : taxData.getPayments()) {
            if (payment.getPaymentValueChf() != null && position.unitsCounter != null) {
              LocalDate paymentDate = payment.getExDate() != null ? payment.getExDate() : payment.getPaymentDate();
              if (paymentDate != null) {
                double unitsAtPayment = position.unitsCounter.getUnitsAtDate(paymentDate);
                double multiplier = calcIctaxMultiplierForUnits(position, unitsAtPayment);
                double paymentTotal = payment.getPaymentValueChf() * multiplier;
                payment.setComputedUnitsAtDate(unitsAtPayment);
                payment.setComputedTotalPaymentChf(paymentTotal);
                totalPayment += paymentTotal;
              }
            }
          }
          position.ictaxTotalPaymentValueChf = totalPayment;
          yearGroup.yearIctaxTotalPaymentValueChf += totalPayment;
        }
      } else {
        position.ictaxTaxValuePerUnitChf = null;
        position.ictaxTotalTaxValueChf = null;
        position.ictaxPayments = null;
        position.ictaxTotalPaymentValueChf = null;
      }
    }
  }

  /**
   * Calculates the multiplier for converting ICTax per-unit values to position totals using year-end units. For bonds
   * (DIRECT_INVESTMENT + FIXED_INCOME/CONVERTIBLE_BOND), ICTax reports values per denomination, while GT tracks units
   * as nominal/100. For all other securities, ICTax values are per share and the multiplier equals units.
   *
   * @param position the security position containing security details and unit count
   * @return the multiplier to apply to ICTax per-unit values
   */
  private double calcIctaxMultiplier(SecurityDividendsPosition position) {
    return calcIctaxMultiplierForUnits(position, position.unitsAtEndOfYear);
  }

  /**
   * Calculates the multiplier for converting ICTax per-unit values to totals for a given unit count. For bonds, adjusts
   * using denomination; for other securities, the multiplier equals the unit count.
   *
   * @param position the security position containing security type and denomination info
   * @param units    the number of units to use for the calculation
   * @return the multiplier to apply to ICTax per-unit values
   */
  private double calcIctaxMultiplierForUnits(SecurityDividendsPosition position, double units) {
    if (position.security.isBondDirectInvestment() && position.security.getDenomination() != null
        && position.security.getDenomination() > 0) {
      return units * 100.0 / position.security.getDenomination();
    }
    return units;
  }

  /**
   * Adjusts all carried-over UnitsCounter values for splits occurring in the given year. Must be called at year
   * boundaries BEFORE processing any transactions for that year, converting counter units from the previous year-end
   * basis to the new year-end basis.
   *
   * @param year                       the year whose splits should be applied
   * @param unitsCounterBySecurityMap  map tracking unit holdings by security
   * @param securitysplitMap           map of security splits for position adjustments
   */
  private void adjustUnitsCounterForNewYear(int year, Map<Integer, UnitsCounter> unitsCounterBySecurityMap,
      Map<Integer, List<Securitysplit>> securitysplitMap,
      Map<Integer, Map<Integer, MarginTracker>> marginOpenTransaction) {
    LocalDate fromDate = LocalDate.of(year, 1, 1);
    LocalDate toDate = LocalDate.of(year, 12, 31);
    for (var entry : unitsCounterBySecurityMap.entrySet()) {
      SplitFactorAfterBefore sfab = Securitysplit.calcSplitFatorForFromDateAndToDate(
          entry.getKey(), fromDate, toDate, securitysplitMap);
      if (sfab.fromToDateFactor != 1.0) {
        entry.getValue().units *= sfab.fromToDateFactor;
      }
    }
    for (var secEntry : marginOpenTransaction.entrySet()) {
      SplitFactorAfterBefore sfab = Securitysplit.calcSplitFatorForFromDateAndToDate(
          secEntry.getKey(), fromDate, toDate, securitysplitMap);
      if (sfab.fromToDateFactor != 1.0) {
        for (MarginTracker mt : secEntry.getValue().values()) {
          mt.applySplitFactor(sfab.fromToDateFactor);
        }
      }
    }
  }

  /**
   * Creates year groups for open security positions even when no transactions occurred in a given year.
   * 
   * <p>
   * This method ensures that securities with open positions are represented in every year, even when no buy/sell or
   * dividend transactions occurred. This is important for maintaining complete position tracking and accurate year-end
   * valuations. It applies security split adjustments to maintain accurate unit counts across years.
   * </p>
   * 
   * @param securityDividendsGrandTotal the grand total object to update
   * @param year                        the year to fill with open positions
   * @param unitsCounterBySecurityMap   map tracking current unit holdings by security
   * @param securitysplitMap            map of security splits for position adjustments
   */
  private void createFillYearWithOpenPositions(final SecurityDividendsGrandTotal securityDividendsGrandTotal,
      final Integer year, final Map<Integer, UnitsCounter> unitsCounterBySecurityMap,
      final Map<Integer, List<Securitysplit>> securitysplitMap, final Map<Integer, Map<Integer, MarginTracker>> marginOpenTransaction) {
    final SecurityDividendsYearGroup securityDividendsYearGroupLast = securityDividendsGrandTotal
        .getOrCreateGroup(year);
    securityDividendsYearGroupLast.fillYearWithOpenPositions(unitsCounterBySecurityMap, securitysplitMap, marginOpenTransaction);
  }

  /**
   * Creates or updates a units counter for tracking security position quantities.
   * 
   * <p>
   * This method maintains running totals of security units held across all transactions. If a counter already exists
   * for the security, the units are added to the existing total. Otherwise, a new counter is created with the initial
   * unit amount.
   * </p>
   * 
   * @param security                  the security entity to track
   * @param addRecudeUntis            the number of units to add (positive) or reduce (negative)
   * @param unitsCounterBySecurityMap map storing units counters by security ID
   */
  private void createOrGetUnitsCounter(final Security security, final Double addRecudeUntis,
      final Map<Integer, UnitsCounter> unitsCounterBySecurityMap, final LocalDate transactionDate) {
    UnitsCounter unitsCounter = unitsCounterBySecurityMap.get(security.getIdSecuritycurrency());
    if (unitsCounter == null) {
      unitsCounter = new UnitsCounter(security, addRecudeUntis);
      unitsCounterBySecurityMap.put(security.getIdSecuritycurrency(), unitsCounter);
    } else {
      unitsCounter.addUnits(addRecudeUntis);
    }
    unitsCounter.recordUnitsAtDate(transactionDate);
    unitsCounter.recordMutation(transactionDate, addRecudeUntis);
  }

  private void trackMarginSecurityUnits(Map<Integer, Map<Integer, MarginTracker>> marginOpenTransaction,
      Transaction transaction, SecurityDividendsPosition securityDividendsPosition, double unitsSplited) {
    if (securityDividendsPosition.security.isMarginInstrument()) {
      final int idSecurity = securityDividendsPosition.security.getId();
      Map<Integer, MarginTracker> securityMarginOpenTransaction = marginOpenTransaction
          .get(securityDividendsPosition.security.getId());
      if (transaction.isMarginOpenPosition()) {
        marginOpenTransaction.computeIfAbsent(idSecurity, k -> new HashMap<>()).put(transaction.getIdTransaction(),
            new MarginTracker(transaction, unitsSplited));
      } else if (transaction.isMarginClosePosition()) {
        MarginTracker marginTracker = securityMarginOpenTransaction.get(transaction.getConnectedIdTransaction());
        marginTracker.openUnits += unitsSplited;
        if (marginTracker.openUnits == 0.0) {
          securityMarginOpenTransaction.remove(transaction.getConnectedIdTransaction());
        }
      }
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
    return historyquoteJpaRepository.getSecuritycurrencyHistoryEndOfYearsByIdTenant(idTenant).stream()
        .collect(Collectors.groupingBy(historyquote -> historyquote.getDate().getYear(),
            Collectors.toMap(Historyquote::getIdSecuritycurrency, Function.identity())));
  }

  /**
   * Marks security positions as excluded from the tax export based on persisted exclusion configuration.
   */
  private void markExcludedSecurities(Integer idTenant, SecurityDividendsGrandTotal grandTotal) {
    for (SecurityDividendsYearGroup yearGroup : grandTotal.getSecurityDividendsYearGroup()) {
      List<TaxSecurityYearConfig> exclusions = taxSecurityYearConfigJpaRepository.findByIdIdTenantAndIdTaxYear(idTenant,
          yearGroup.year.shortValue());
      if (!exclusions.isEmpty()) {
        Set<Integer> excludedIds = exclusions.stream().map(TaxSecurityYearConfig::getIdSecuritycurrency)
            .collect(Collectors.toSet());
        for (SecurityDividendsPosition position : yearGroup.getSecurityDividendsPositions()) {
          if (excludedIds.contains(position.security.getIdSecuritycurrency())) {
            position.excludedFromTax = true;
          }
        }
      }
    }
  }

}
