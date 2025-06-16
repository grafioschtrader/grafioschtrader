package grafioschtrader.reports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.common.DateHelper;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.common.DataBusinessHelper.CashaccountTransfer;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.instrument.SecurityCalcService;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.account.AccountPositionGrandSummary;
import grafioschtrader.reportviews.account.AccountPositionGroupSummary;
import grafioschtrader.reportviews.account.CashaccountPositionSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionCurrenyGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.repository.helper.AccountGroupMap;
import grafioschtrader.repository.helper.GroupPortfolio;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TransactionType;

/**
 * Comprehensive financial reporting service that generates combined position summaries for cash accounts and security
 * accounts across portfolios. Provides consolidated views of portfolio holdings with multi-currency support and
 * intelligent exchange rate handling.
 * 
 * <p>
 * This report generator is the primary tool for creating portfolio position reports that combine cash balances,
 * security holdings, realized and unrealized gains/losses, and foreign exchange impacts. It supports both tenant-wide
 * and individual portfolio reporting with flexible grouping capabilities.
 * </p>
 * 
 * <h3>Key Features:</h3>
 * <ul>
 * <li>Multi-currency portfolio position calculations with automated exchange rate resolution</li>
 * <li>Combined cash account and security account reporting</li>
 * <li>Realized and unrealized gain/loss calculations</li>
 * <li>Foreign exchange gain/loss tracking for multi-currency positions</li>
 * <li>Flexible grouping by currency, portfolio, or custom criteria</li>
 * <li>Historical and current market value reporting</li>
 * <li>Dividend, interest, and fee tracking with tax considerations</li>
 * </ul>
 * 
 * <h3>Report Types:</h3>
 * <ul>
 * <li>Tenant-wide grand summary across all portfolios</li>
 * <li>Individual portfolio position summary</li>
 * <li>Currency-grouped position summaries</li>
 * <li>Security account and cash account breakdowns</li>
 * </ul>
 * 
 * <p>
 * The service handles scenarios including margin trading, currency conversions, stock splits, dividend distributions,
 * and inter-account transfers while maintaining accurate accounting principles and audit trails.
 * </p>
 */
@Component
public class AccountPositionGroupSummaryReport extends SecurityCashaccountGroupByCurrencyBaseReport {

  @Autowired
  protected SecurityCalcService securityCalcService;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private PortfolioJpaRepository portfolioJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  private GlobalparametersService globalparametersService;

  /**
   * Constructs the report generator with required dependencies for trading calendar and currency precision handling.
   * 
   * @param tradingDaysPlusJpaRepository repository for trading calendar operations
   * @param globalparametersService      service providing system-wide configuration parameters
   */
  public AccountPositionGroupSummaryReport(TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository,
      GlobalparametersService globalparametersService) {
    super(tradingDaysPlusJpaRepository, globalparametersService.getCurrencyPrecision());
    this.globalparametersService = globalparametersService;
  }

  /**
   * Generates a comprehensive position summary for all portfolios within a tenant, grouped according to the specified
   * criteria (e.g., by currency, asset class, or portfolio). Provides a consolidated view of the tenant's entire
   * investment portfolio with multi-currency normalization to the tenant's main currency.
   * 
   * <p>
   * This method asynchronously loads historical exchange rates and currency pairs to ensure accurate multi-currency
   * calculations. It processes all transactions across all portfolios up to the specified date and calculates current
   * market values, realized/unrealized gains, and foreign exchange impacts.
   * </p>
   * 
   * @param idTenant  the unique identifier of the tenant
   * @param grouping  the grouping strategy for organizing results (currency, portfolio, etc.)
   * @param untilDate the cut-off date for including transactions and valuations
   * @return comprehensive grand summary containing all portfolio positions grouped as specified
   */
  public AccountPositionGrandSummary getAccountGrandSummaryIdTenant(final Integer idTenant,
      final AccountGroupMap<?> grouping, final Date untilDate) {
    final Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);

    final CompletableFuture<List<Object[]>> dateTransactionCurrencyFuture = CompletableFuture
        .supplyAsync(() -> historyquoteJpaRepository.getHistoryquotesForAllForeignTransactionsByIdTenant(idTenant));
    final CompletableFuture<List<Currencypair>> currencypairsFuture = CompletableFuture
        .supplyAsync(() -> currencypairJpaRepository.getAllCurrencypairsForTenantByTenant(idTenant));

    final DateTransactionCurrencypairMap dateCurrencyMap = new DateTransactionCurrencypairMap(tenant.getCurrency(),
        untilDate, dateTransactionCurrencyFuture.join(), currencypairsFuture.join(),
        tradingDaysPlusJpaRepository.hasTradingDayBetweenUntilYesterday(DateHelper.getLocalDate(untilDate)), false);
    getAccountSummaryPositionSummary(tenant.getPortfolioList(), grouping, tenant.getCurrency(), idTenant,
        tenant.isExcludeDivTax(), dateCurrencyMap);

    return grouping.getGrandGroupSummary(dateCurrencyMap,
        globalparametersService.getPrecisionForCurrency(tenant.getCurrency()));
  }

  /**
   * Calculates and returns a detailed position summary for a single portfolio, including all cash accounts and security
   * positions with current market valuations. Validates tenant ownership before processing to ensure data security.
   * 
   * <p>
   * This method provides a comprehensive view of a single portfolio's holdings, including cash balances, security
   * positions, unrealized gains/losses, and foreign exchange impacts. It handles portfolios with multiple currencies
   * and normalizes all values to the portfolio's base currency.
   * </p>
   * 
   * @param idTenant    the tenant ID for security validation
   * @param idPortfolio the unique identifier of the portfolio to analyze
   * @param untilDate   the cut-off date for transactions and market valuations
   * @return detailed position summary for the specified portfolio
   * @throws SecurityException if the portfolio doesn't belong to the specified tenant
   */
  public AccountPositionGroupSummary getAccountGrandSummaryPortfolio(Integer idTenant, final Integer idPortfolio,
      final Date untilDate) {

    final Portfolio portfolio = this.portfolioJpaRepository.getReferenceById(idPortfolio);
    if (portfolio.getIdTenant().equals(idTenant)) {
      if (portfolio.getCashaccountList() != null && !portfolio.getCashaccountList().isEmpty()) {
        final GroupPortfolio groupPortfolio = new GroupPortfolio();
        final Tenant tenant = tenantJpaRepository.getReferenceById(portfolio.getIdTenant());

        final CompletableFuture<List<Object[]>> dateTransactionCurrencyFuture = CompletableFuture.supplyAsync(
            () -> historyquoteJpaRepository.getHistoryquotesForAllForeignTransactionsByIdPortfolio(idPortfolio));

        final CompletableFuture<List<Currencypair>> currencypairsFuture = CompletableFuture
            .supplyAsync(() -> currencypairJpaRepository.getAllCurrencypairsForPortfolioByPortfolio(idPortfolio));

        final DateTransactionCurrencypairMap dateCurrencyMap = new DateTransactionCurrencypairMap(
            portfolio.getCurrency(), untilDate, dateTransactionCurrencyFuture.join(), currencypairsFuture.join(),
            tradingDaysPlusJpaRepository.hasTradingDayBetweenUntilYesterday(DateHelper.getLocalDate(untilDate)), false);
        getAccountSummaryPositionSummary(Arrays.asList(portfolio), groupPortfolio, portfolio.getCurrency(),
            portfolio.getIdTenant(), tenant.isExcludeDivTax(), dateCurrencyMap);
        final AccountPositionGroupSummary accountPositionGroupSummary = groupPortfolio.getGroupSummaryList().get(0);
        accountPositionGroupSummary.calcTotals(dateCurrencyMap);

        return accountPositionGroupSummary;
      } else {
        return new AccountPositionGroupSummary(portfolio.getName(), portfolio.getCurrency());
      }
    } else {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }

  }

  /**
   * Orchestrates the complete position calculation process for a collection of portfolios. Processes all transaction
   * data, calculates current positions, and applies grouping logic to generate organized position summaries with
   * multi-currency support.
   * 
   * <p>
   * This is the main processing engine that coordinates cash account processing, security position calculations,
   * currency conversions, and final aggregation. It handles scenarios including margin trading, foreign exchange
   * transactions, and inter-portfolio transfers.
   * </p>
   * 
   * @param portfolioList     the portfolios to include in the analysis
   * @param accountGroupMap   the grouping strategy for organizing results
   * @param mainCurrency      the base currency for conversions and reporting
   * @param idTenant          the tenant identifier for data access control
   * @param excludeDivTaxcost whether to exclude dividend tax costs from calculations
   * @param dateCurrencyMap   the currency exchange rate context for conversions
   */
  private void getAccountSummaryPositionSummary(final List<Portfolio> portfolioList,
      final AccountGroupMap<?> accountGroupMap, final String mainCurrency, final Integer idTenant,
      final boolean excludeDivTaxcost, final DateTransactionCurrencypairMap dateCurrencyMap) {

    Map<Integer, Double> exchangeRateConnectedTransactionMap = new HashMap<>();
    ReportHelper.loadUntilDateHistoryquotes(idTenant, historyquoteJpaRepository, dateCurrencyMap);

    final long untilDateTime = DateHelper.setTimeToZeroAndAddDay(dateCurrencyMap.getUntilDate(), 1).getTime();
    final AccessCashaccountPositionSummary accessCashaccountPositionSummary = new AccessCashaccountPositionSummary(
        globalparametersService.getCurrencyPrecision());

    final Map<Integer, List<Securitysplit>> securitysplitMap = securitysplitJpaRepository
        .getSecuritysplitMapByIdTenant(idTenant);

    for (final Portfolio portfolio : portfolioList) {
      accessCashaccountPositionSummary.preparteForNewPortfolio();
      final List<Transaction> everyKindOfTransactionsUntilDate = new ArrayList<>();
      portfolio.getCashaccountList().forEach(cashaccount -> {
        accessCashaccountPositionSummary.createAndAddCashaccountPositionSummary(cashaccount, dateCurrencyMap);
        everyKindOfTransactionsUntilDate.addAll(cashaccount.getTransactionList().stream()
            .filter(transaction -> transaction.getTransactionTime().getTime() < untilDateTime)
            .collect(Collectors.toList()));
      });

      Collections.sort(everyKindOfTransactionsUntilDate);
      portfolioCalcEveryTransaction(everyKindOfTransactionsUntilDate, accessCashaccountPositionSummary, mainCurrency,
          dateCurrencyMap, exchangeRateConnectedTransactionMap);

      final Map<Security, SecurityPositionSummary> securityPositionSummaryMap = calcSecurityTransaction(
          everyKindOfTransactionsUntilDate, securitysplitMap, excludeDivTaxcost, dateCurrencyMap);

      final CurrencySecurityaccountCurrenyResult currencySecurityaccountCurrenyResult = calcAndCreatePortfolioSeucrityTotalPerCurrency(
          securityPositionSummaryMap, dateCurrencyMap, portfolio.getCashaccountList());

      portfolioEndCalc(accountGroupMap, accessCashaccountPositionSummary, currencySecurityaccountCurrenyResult,
          dateCurrencyMap);

    }
    this.currencypairJpaRepository.calcGainLossBasedOnDateOrNewestPrice(accountGroupMap.getAllForeignCurrency(),
        dateCurrencyMap.getUntilDate());
  }

  //////////////////////////////////////////////
  // For every transaction on a portfolio
  //////////////////////////////////////////////
  /**
   * Processes every transaction within a portfolio chronologically to build up cash account balances, track fees and
   * interest, and handle multi-currency transactions. Maintains accurate accounting for deposits, withdrawals,
   * transfers, and currency exchanges.
   * 
   * <p>
   * This method handles various transaction types including fees, interest, deposits, withdrawals, and foreign exchange
   * transactions. It properly accounts for cash transfers between accounts and maintains currency-specific balances for
   * accurate foreign exchange gain/loss calculations.
   * </p>
   * 
   * @param everyKindOfTransactions             chronologically sorted list of all transactions
   * @param acps                                cash account position summary accessor for organizing results
   * @param mainCurrency                        the base currency for normalization
   * @param dateCurrencyMap                     currency exchange rate context
   * @param exchangeRateConnectedTransactionMap cache for connected transaction exchange rates
   */
  private void portfolioCalcEveryTransaction(final List<Transaction> everyKindOfTransactions,
      final AccessCashaccountPositionSummary acps, final String mainCurrency,
      final DateTransactionCurrencypairMap dateCurrencyMap, Map<Integer, Double> exchangeRateConnectedTransactionMap) {

    for (final Transaction transaction : everyKindOfTransactions) {

      final CashaccountPositionSummary accountPositionSummary = acps.cashaccountPositionSummaryByCashaccountMap
          .get(transaction.getCashaccount());

      accountPositionSummary.cashBalance += transaction.getCashaccountAmount();
      if (transaction.getTransactionType() == TransactionType.FEE) {
        accountPositionSummary.accountFeesMC += transaction.getFeeMC(dateCurrencyMap);
      } else if (transaction.getTransactionType() == TransactionType.INTEREST_CASHACCOUNT) {
        accountPositionSummary.accountInterestMC += transaction.getInterestMC(dateCurrencyMap);
      } else if (mainCurrency.equals(transaction.getCashaccount().getCurrency())
          && (transaction.getTransactionType() == TransactionType.DEPOSIT
              || transaction.getTransactionType() == TransactionType.WITHDRAWAL)) {
        // Withdrawal and deposit with the main currency
        if (transaction.getIdCurrencypair() == null) {
          accountPositionSummary.externalCashTransferMC += transaction.getCashaccountAmount()
              + (transaction.getTransactionCost() != null ? transaction.getTransactionCost() : 0.0);
        }
      }

      if (transaction.getTransactionType() == TransactionType.DEPOSIT
          || transaction.getTransactionType() == TransactionType.WITHDRAWAL) {
        CashaccountTransfer ct = DataBusinessHelper.calcDepositOnTransactionsOfCashaccount(transaction,
            dateCurrencyMap.getFromToCurrencyWithDateMap(), mainCurrency, exchangeRateConnectedTransactionMap,
            dateCurrencyMap.getCurrencypairFromToCurrencyMap());

        accountPositionSummary.cashTransferMC += ct.amountMC;
        accountPositionSummary.cashAccountTransactionFeeMC += ct.cashAccountTransactionFeeMC;
      }

      processNoMainCurrencyTransaction(acps, accountPositionSummary, mainCurrency, dateCurrencyMap, transaction);
    }
  }

  /**
   * Calculates security position summaries for all security transactions within the reporting period. Delegates to the
   * security calculation service to handle scenarios including stock splits, dividend distributions, and margin trading
   * positions.
   * 
   * @param everyKindOfTransactionsUntilDate all transactions to process for security calculations
   * @param securitysplitMap                 mapping of securities to their historical stock splits
   * @param excludeDivTaxcost                whether to exclude dividend tax costs from position calculations
   * @param dateCurrencyMap                  currency exchange rate context for multi-currency securities
   * @return map of securities to their calculated position summaries
   */
  private Map<Security, SecurityPositionSummary> calcSecurityTransaction(
      final List<Transaction> everyKindOfTransactionsUntilDate,
      final Map<Integer, List<Securitysplit>> securitysplitMap, final boolean excludeDivTaxcost,
      final DateTransactionCurrencypairMap dateCurrencyMap) {
    final Map<Security, SecurityPositionSummary> securityPositionSummaryMap = new HashMap<>();
    for (final Transaction transaction : everyKindOfTransactionsUntilDate) {
      if (transaction.getSecurity() != null) {
        securityCalcService.calcSingleSecurityTransaction(transaction, securityPositionSummaryMap, securitysplitMap,
            excludeDivTaxcost, dateCurrencyMap);
      }
    }
    return securityPositionSummaryMap;
  }

  /**
   * Handles transactions involving currencies different from the main currency to properly account for foreign exchange
   * impacts and maintain accurate currency-specific balances. Manages scenarios including cross-currency transactions
   * and pseudo-account creation.
   * 
   * <p>
   * This method is critical for accurate foreign exchange gain/loss calculations. It handles various scenarios
   * including transactions between foreign currencies, currency exchanges involving the main currency, and
   * deposits/withdrawals in foreign currencies without explicit currency exchange.
   * </p>
   * 
   * @param acps                   cash account position summary accessor
   * @param accountPositionSummary the specific account position being processed
   * @param mainCurrency           the base currency for the tenant/portfolio
   * @param dateCurrencyMap        currency exchange rate context
   * @param transaction            the transaction to process
   */
  private void processNoMainCurrencyTransaction(final AccessCashaccountPositionSummary acps,
      final CashaccountPositionSummary accountPositionSummary, final String mainCurrency,
      final DateTransactionCurrencypairMap dateCurrencyMap, final Transaction transaction) {

    if (transaction.getIdCurrencypair() != null) {
      // Transaction with a currency that differs from the currency of the cash
      // account of the transaction
      ////////////////////////////////////////////////////////////////////////////////////////////////////
      final Currencypair currencypair = dateCurrencyMap
          .getCurrencypairByIdCurrencypair(transaction.getIdCurrencypair());

      if (!accountPositionSummary.getCashaccount().getCurrency().equals(mainCurrency)
          && !currencypair.getToCurrency().equals(mainCurrency)
          && !currencypair.getFromCurrency().equals(mainCurrency)) {
        // Transaction that happened between two different currencies but none of them
        // is the main currency.
        this.foreignCurrencyTransaction(currencypair, acps, accountPositionSummary, dateCurrencyMap, transaction);
      } else if (accountPositionSummary.getCashaccount().getCurrency().equals(mainCurrency)) {
        // It is an exchange on the cash account with the main currency
        // For example buy or sell with main currency a security on foreign currency

        final CashaccountPositionSummary cashaccountPositionSummary = getOrCreatePseudoAccountPositionGroupSummary(acps,
            accountPositionSummary.getCashaccount().getPortfolio(), currencypair.getFromCurrency(), dateCurrencyMap);
        if (cashaccountPositionSummary != null) {
          double cashaccountAmount = transaction.getCashaccountAmount();
          cashaccountPositionSummary.balanceCurrencyTransaction -= cashaccountAmount / transaction.getCurrencyExRate();
          cashaccountPositionSummary.balanceCurrencyTransactionMC -= cashaccountAmount;
        }

      } else if (currencypair.getFromCurrency().equals(mainCurrency)) {
        accountPositionSummary.balanceCurrencyTransaction += transaction.getCashaccountAmount();
        accountPositionSummary.balanceCurrencyTransactionMC += transaction.getCashaccountAmount()
            / transaction.getCurrencyExRate();
      }

    } else if (!transaction.getCashaccount().getCurrency().equals(mainCurrency)
        && transaction.getIdCurrencypair() == null
        && (transaction.getTransactionType() == TransactionType.DEPOSIT
            || transaction.getTransactionType() == TransactionType.FEE
            || transaction.getTransactionType() == TransactionType.INTEREST_CASHACCOUNT
            || transaction.getTransactionType() == TransactionType.WITHDRAWAL)) {
      // it is a deposit or withdrawal on foreign cash account without currency
      // exchange
      final Double exchangeRate = dateCurrencyMap.getPriceByDateAndFromCurrency(transaction.getTransactionDateAsDate(),
          accountPositionSummary.securitycurrency.getFromCurrency(), true);

      accountPositionSummary.balanceCurrencyTransaction += transaction.getCashaccountAmount();
      accountPositionSummary.balanceCurrencyTransactionMC += transaction.getCashaccountAmount() * exchangeRate;
      if (transaction.getTransactionType() == TransactionType.DEPOSIT
          || transaction.getTransactionType() == TransactionType.WITHDRAWAL) {
        accountPositionSummary.externalCashTransferMC += (transaction.getCashaccountAmount()
            + (transaction.getTransactionCost() != null ? transaction.getTransactionCost() : 0.0)) * exchangeRate;
      }

    }
  }

  /**
   * Handles foreign currency transactions where neither currency involved is the main currency. Manages cash account
   * transfers and cross-currency trading with proper exchange rate tracking and pseudo-account creation as needed.
   * 
   * <p>
   * This method handles the most currency scenarios, including transfers between two foreign cash accounts and
   * transactions involving foreign securities purchased with foreign currency. It maintains accurate exchange rate
   * tracking for connected transactions and creates pseudo-accounts for currency balances that don't have dedicated
   * cash accounts.
   * </p>
   * 
   * @param currencypair           the currency pair involved in the transaction
   * @param acps                   cash account position summary accessor
   * @param accountPositionSummary the account position being processed
   * @param dateCurrencyMap        currency exchange rate context
   * @param transaction            the foreign currency transaction to process
   */
  private void foreignCurrencyTransaction(final Currencypair currencypair, final AccessCashaccountPositionSummary acps,
      final CashaccountPositionSummary accountPositionSummary, final DateTransactionCurrencypairMap dateCurrencyMap,
      final Transaction transaction) {
    // Transfer between two foreign cash accounts

    if (transaction.isCashaccountTransfer()) {
      // Transfer between two foreign cash accounts
      Double exchangeRate = acps.exchangeRateConnectedTransactionMap.get(transaction.getIdTransaction());
      if (exchangeRate == null) {
        // different currency, for example USD/GBP when USD nor GBP is main currency
        exchangeRate = dateCurrencyMap.getPriceByDateAndFromCurrency(transaction.getTransactionDateAsDate(),
            accountPositionSummary.securitycurrency.getFromCurrency(), true);

        double normalizedExchangeRate = currencypair.getFromCurrency()
            .equals(accountPositionSummary.getCashaccount().getCurrency())
                ? exchangeRate / transaction.getCurrencyExRateNotNull()
                : exchangeRate * transaction.getCurrencyExRateNotNull();

        acps.exchangeRateConnectedTransactionMap.put(transaction.getConnectedIdTransaction(), normalizedExchangeRate);
      } else {
        // Same currency
        acps.exchangeRateConnectedTransactionMap.remove(transaction.getIdTransaction());
      }

      accountPositionSummary.balanceCurrencyTransaction += transaction.getCashaccountAmount();
      accountPositionSummary.balanceCurrencyTransactionMC += transaction.getCashaccountAmount() * exchangeRate;
    } else {
      final Double exchangeRate = dateCurrencyMap.getPriceByDateAndFromCurrency(transaction.getTransactionDateAsDate(),
          accountPositionSummary.securitycurrency.getFromCurrency(), true);
      // Transaction between to currencies when main currency is not involved.
      // TODO Fix many currency should not be involved
      // Transaction 40024 GBP / USD
      accountPositionSummary.balanceCurrencyTransaction += transaction.getCashaccountAmount();
      accountPositionSummary.balanceCurrencyTransactionMC += transaction.getCashaccountAmount() * exchangeRate;
      final CashaccountPositionSummary securityACPS = getOrCreatePseudoAccountPositionGroupSummary(acps,
          accountPositionSummary.getCashaccount().getPortfolio(), transaction.getSecurity().getCurrency(),
          dateCurrencyMap);

      securityACPS.balanceCurrencyTransaction -= transaction.getCashaccountAmount() / transaction.getCurrencyExRate();
      securityACPS.balanceCurrencyTransactionMC -= transaction.getCashaccountAmount() * exchangeRate;
    }
  }

  /**
   * Retrieves an existing cash account position summary for a currency, or creates a new pseudo-account if none exists.
   * Used for tracking currency balances when explicit cash accounts don't exist for every currency involved in
   * transactions.
   * 
   * @param acps            cash account position summary accessor
   * @param portfolio       the portfolio context for the pseudo-account
   * @param currency        the currency for which to find or create an account summary
   * @param dateCurrencyMap currency exchange rate context
   * @return existing or newly created cash account position summary for the currency
   */
  private CashaccountPositionSummary getOrCreatePseudoAccountPositionGroupSummary(
      final AccessCashaccountPositionSummary acps, final Portfolio portfolio, final String currency,
      final DateTransactionCurrencypairMap dateCurrencyMap) {

    CashaccountPositionSummary cashaccountPositionSummary = acps.cashaccountPositionSummaryByCurrencyMap.get(currency);
    if (cashaccountPositionSummary == null) {
      final Cashaccount cashaccount = new Cashaccount();
      cashaccount.setPortfolio(portfolio);
      cashaccount.setCurrency(currency);
      cashaccountPositionSummary = acps.createAndAddCashaccountPositionSummary(cashaccount, dateCurrencyMap);
    }
    return cashaccountPositionSummary;
  }

  //////////////////////////////////////////////
  // For every Portfolio
  //////////////////////////////////////////////

  /**
   * Calculates and groups security position summaries by currency and security account, applying current market prices
   * to open positions and organizing results for final reporting. Handles both open and closed positions with proper
   * market valuation.
   * 
   * @param securityPositionSummaryMap calculated security positions by security
   * @param dateCurrencyMap            currency exchange rate context for market valuations
   * @param cashaccountList            list of cash accounts to determine security account groupings
   * @return organized security position results grouped by currency and security account
   */
  private CurrencySecurityaccountCurrenyResult calcAndCreatePortfolioSeucrityTotalPerCurrency(
      final Map<Security, SecurityPositionSummary> securityPositionSummaryMap,
      final DateTransactionCurrencypairMap dateCurrencyMap, final List<Cashaccount> cashaccountList) {

    final List<SecurityPositionSummary> securityPositionSummaryList = securityJpaRepository
        .processOpenPositionsWithActualPrice(dateCurrencyMap.getUntilDate(), securityPositionSummaryMap);
    final List<SecurityPositionSummary> closedSecurityPositionList = securityPositionSummaryMap.entrySet().stream()
        .filter(map -> map.getValue().units == 0).map(map -> map.getValue()).collect(Collectors.toList());
    securityPositionSummaryList.addAll(closedSecurityPositionList);

    final Set<SeperateSecurityaccountCurrency> seperateSecurityaccountCurrencySet = cashaccountList.stream()
        .filter(cashaccount -> cashaccount.getConnectIdSecurityaccount() != null)
        .map(cashaccount -> new SeperateSecurityaccountCurrency(cashaccount.getCurrency(),
            cashaccount.getConnectIdSecurityaccount()))
        .collect(Collectors.toSet());

    return createAndCalcSubtotalsPerCurrencyAndIdSecurityaccount(historyquoteJpaRepository, securityPositionSummaryList,
        dateCurrencyMap, seperateSecurityaccountCurrencySet, globalparametersService.getCurrencyPrecision());

  }

  /**
   * Finalizes the portfolio calculation by combining cash account and security account results for each account,
   * applying the appropriate grouping strategy, and preparing the final organized position summaries for reporting.
   * 
   * @param accountGroupMap the grouping strategy for organizing final results
   * @param acps            cash account position summary accessor containing calculated cash positions
   * @param cscr            security position results organized by currency and security account
   * @param dateCurrencyMap currency exchange rate context for final calculations
   */
  private void portfolioEndCalc(final AccountGroupMap<?> accountGroupMap, final AccessCashaccountPositionSummary acps,
      final CurrencySecurityaccountCurrenyResult cscr, final DateTransactionCurrencypairMap dateCurrencyMap) {

    acps.cashaccountPositionSummaryByCashaccountMap.values().stream().forEach(cashaccountPositionSummary -> {
      final Cashaccount cashaccount = cashaccountPositionSummary.getCashaccount();

      final AccountPositionGroupSummary accountPositionGroupSummary = accountGroupMap
          .getAccountPositionGroupSummary(cashaccount);

      final CashaccountPositionSummary accountPositionSummary = acps.cashaccountPositionSummaryByCashaccountMap
          .get(cashaccount);

      SecurityPositionCurrenyGroupSummary securityPositionCurrenyGroupSummary = null;

      if (cashaccount.getConnectIdSecurityaccount() != null) {
        securityPositionCurrenyGroupSummary = cscr.securityaccountCurrencyTotalMap.get(
            new SeperateSecurityaccountCurrency(cashaccount.getCurrency(), cashaccount.getConnectIdSecurityaccount()));
      }
      if (securityPositionCurrenyGroupSummary == null) {
        securityPositionCurrenyGroupSummary = cscr.currencyTotalMap.get(cashaccount.getCurrency());
        cscr.currencyTotalMap.remove(cashaccount.getCurrency());
      }

      combineAndCalculateSummaries(accountPositionGroupSummary, accountPositionSummary,
          securityPositionCurrenyGroupSummary);

    });

  }

  /**
   * Combines cash account and security account position summaries into a unified account position summary for final
   * reporting. Links security position data with the corresponding cash account position summary.
   * 
   * @param accountPositionGroupSummary         the group summary to add the combined position to
   * @param accountPositionSummary              the cash account position summary
   * @param securityPositionCurrenyGroupSummary the security position summary for the same currency/account
   */
  private void combineAndCalculateSummaries(final AccountPositionGroupSummary accountPositionGroupSummary,
      final CashaccountPositionSummary accountPositionSummary,
      final SecurityPositionCurrenyGroupSummary securityPositionCurrenyGroupSummary) {

    accountPositionSummary.setSecuritiesValue(securityPositionCurrenyGroupSummary);
    accountPositionGroupSummary.accountPositionSummaryList.add(accountPositionSummary);
  }

}

/**
 * Internal helper class that manages cash account position summaries during report generation. Provides organized
 * access to position summaries by cash account and by currency, with support for creating pseudo-accounts for
 * currencies that don't have explicit cash accounts.
 * 
 * <p>
 * This class serves as a centralized accessor for cash account position data during the multi-portfolio, multi-currency
 * calculation process. It maintains mappings for efficient lookup and handles the creation of temporary pseudo-accounts
 * needed for comprehensive currency tracking.
 * </p>
 */
class AccessCashaccountPositionSummary {

  /**
   * Cache for exchange rates used in connected transactions involving foreign currencies. Maps transaction ID to the
   * calculated exchange rate for the connected transaction pair. Used to ensure consistent exchange rate application
   * across related transfer transactions.
   */
  public Map<Integer, Double> exchangeRateConnectedTransactionMap = new HashMap<>();

  /** Maps each cash account to its corresponding position summary for direct access */
  public Map<Cashaccount, CashaccountPositionSummary> cashaccountPositionSummaryByCashaccountMap;

  /** Maps each currency to its position summary, including pseudo-accounts for currency tracking */
  public Map<String, CashaccountPositionSummary> cashaccountPositionSummaryByCurrencyMap;

  /** Counter for generating unique IDs for pseudo-accounts that don't exist in the database */
  private Integer idCounter = 0;

  /** Currency precision configuration for proper rounding in calculations */
  private Map<String, Integer> currencyPrecisionMap;

  public AccessCashaccountPositionSummary(Map<String, Integer> currencyPrecisionMap) {
    this.currencyPrecisionMap = currencyPrecisionMap;
  }

  public void preparteForNewPortfolio() {
    cashaccountPositionSummaryByCashaccountMap = new HashMap<>();
    cashaccountPositionSummaryByCurrencyMap = new HashMap<>();
  }

  /**
   * Creates a new cash account position summary and adds it to both mapping structures for efficient access during
   * calculations. Handles the creation of pseudo-accounts for currencies that don't have dedicated cash accounts.
   * 
   * @param cashaccount     the cash account to create a position summary for
   * @param dateCurrencyMap currency exchange rate context for currency pair resolution
   * @return the newly created and registered cash account position summary
   */
  public CashaccountPositionSummary createAndAddCashaccountPositionSummary(final Cashaccount cashaccount,
      final DateTransactionCurrencypairMap dateCurrencyMap) {
    if (cashaccount.getIdSecuritycashAccount() == null) {
      // For a non existing cash account with a certain currency
      cashaccount.setIdSecuritycashAccount(--idCounter);
    }

    final CashaccountPositionSummary cashaccountPositionSummary = createCashaccountPositionSummary(cashaccount,
        dateCurrencyMap);
    cashaccountPositionSummaryByCashaccountMap.put(cashaccount, cashaccountPositionSummary);
    cashaccountPositionSummaryByCurrencyMap.put(cashaccount.getCurrency(), cashaccountPositionSummary);

    return cashaccountPositionSummary;
  }

  /**
   * Creates and initializes a new CashaccountPositionSummary with proper currency exchange rate context. Sets up the
   * currency pair relationship if the cash account currency differs from the main currency for accurate foreign
   * exchange calculations.
   * 
   * @param cashaccount     the cash account to create a position summary for
   * @param dateCurrencyMap currency exchange rate context for foreign currency handling
   * @return fully initialized cash account position summary ready for transaction processing
   */
  private CashaccountPositionSummary createCashaccountPositionSummary(final Cashaccount cashaccount,
      final DateTransactionCurrencypairMap dateCurrencyMap) {
    final CashaccountPositionSummary accountPositionSummary = new CashaccountPositionSummary(currencyPrecisionMap);

    if (!dateCurrencyMap.getMainCurrency().equals(cashaccount.getCurrency())) {
      accountPositionSummary.securitycurrency = dateCurrencyMap
          .getCurrencypairByFromCurrency(cashaccount.getCurrency());
    }
    accountPositionSummary.cashBalance = 0.0;
    accountPositionSummary.externalCashTransferMC = 0.0;
    accountPositionSummary.setCashaccount(cashaccount);
    accountPositionSummary.hasTransaction = cashaccount.getTransactionList() != null
        && cashaccount.getTransactionList().size() > 0;
    return accountPositionSummary;
  }

}
