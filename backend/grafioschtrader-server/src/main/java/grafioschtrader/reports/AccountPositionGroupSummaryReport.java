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

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DataHelper.CashaccountTransfer;
import grafioschtrader.common.DateHelper;
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
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.repository.helper.AccountGroupMap;
import grafioschtrader.repository.helper.GroupPortfolio;
import grafioschtrader.types.TransactionType;

/**
 * Creates a report that combines the summary of cash account and security
 * account.
 *
 *
 * @author Hugo Graf
 *
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

  private GlobalparametersJpaRepository globalparametersJpaRepository;

  public AccountPositionGroupSummaryReport(TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository,
      GlobalparametersJpaRepository globalparametersJpaRepository) {
    super(tradingDaysPlusJpaRepository, globalparametersJpaRepository.getCurrencyPrecision());
    this.globalparametersJpaRepository = globalparametersJpaRepository;
  }

  /**
   * Creates a total for all portfolios for a certain group like currencies.
   *
   * @param idTenant
   * @param grouping
   * @return
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
        globalparametersJpaRepository.getPrecisionForCurrency(tenant.getCurrency()));
  }

  /**
   * Calculates the cash- and security account for a single portfolio.
   *
   * @param idPortfolio
   * @return
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
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }

  }

  /**
   * Collects the transaction data for every asked cash account.
   *
   * @param cashaccountList
   * @param accountGroupMap
   * @param mainCurrency
   * @param idTenant
   * @param excludeDivTaxcost
   */
  private void getAccountSummaryPositionSummary(final List<Portfolio> portfolioList,
      final AccountGroupMap<?> accountGroupMap, final String mainCurrency, final Integer idTenant,
      final boolean excludeDivTaxcost, final DateTransactionCurrencypairMap dateCurrencyMap) {

    Map<Integer, Double> exchangeRateConnectedTransactionMap = new HashMap<>();
    ReportHelper.loadUntilDateHistoryquotes(idTenant, historyquoteJpaRepository, dateCurrencyMap);

    final long untilDateTime = DateHelper.setTimeToZeroAndAddDay(dateCurrencyMap.getUntilDate(), 1).getTime();
    final AccessCashaccountPositionSummary accessCashaccountPositionSummary = new AccessCashaccountPositionSummary(
        globalparametersJpaRepository.getCurrencyPrecision());

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
  private void portfolioCalcEveryTransaction(final List<Transaction> everyKindOfTransactions,
      final AccessCashaccountPositionSummary acps, final String mainCurrency,
      final DateTransactionCurrencypairMap dateCurrencyMap, Map<Integer, Double> exchangeRateConnectedTransactionMap) {

    for (final Transaction transaction : everyKindOfTransactions) {

      final CashaccountPositionSummary accountPositionSummary = acps.cashaccountPositionSummaryByCashaccountMap
          .get(transaction.getCashaccount());

      accountPositionSummary.cashBalance += transaction.getCashaccountAmount();
      if (transaction.getTransactionType() == TransactionType.FEE) {
        accountPositionSummary.accountFeesMC += transaction.getFeeExRate(dateCurrencyMap);
      } else if (transaction.getTransactionType() == TransactionType.INTEREST_CASHACCOUNT) {
        accountPositionSummary.accountInterestMC += transaction.getInterestExRate(dateCurrencyMap);
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
        CashaccountTransfer ct = DataHelper.calcDepositOnTransactionsOfCashaccount(transaction,
            dateCurrencyMap.getFromToCurrencyWithDateMap(), mainCurrency, exchangeRateConnectedTransactionMap,
            dateCurrencyMap.getCurrencypairFromToCurrencyMap());

        accountPositionSummary.cashTransferMC += ct.amountMC;
        accountPositionSummary.cashAccountTransactionFeeMC += ct.cashAccountTransactionFeeMC;
      }

      processNoMainCurrencyTransaction(acps, accountPositionSummary, mainCurrency, dateCurrencyMap, transaction);
    }
  }

  /**
   * Calculate the Security transactions.
   *
   * @param everyKindOfTransactionsUntilDate
   * @param securitysplitMap
   * @param excludeDivTaxcost
   * @return
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
   * Processes transaction which has a different currency than main currency.
   * Mainly it prepares the calculation for the gain/loss on currencies.
   *
   *
   * A dividend
   *
   * @param acps
   * @param accountPositionSummary
   * @param mainCurrency
   * @param currencypairMap
   * @param dateCurrencyMap
   * @param transaction
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
   * Transaction that happened between two different currencies but none of them
   * is the main currency.
   *
   * @param currencypair
   * @param acps
   * @param accountPositionSummary
   * @param dateCurrencyMap
   * @param transaction
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
   * Groups every security to its required group.
   *
   *
   * @param securityPositionSummaryMap
   * @param mainCurrency
   * @param currencypairByFromCurrencyMap
   * @param cashaccountList
   * @return
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
        dateCurrencyMap, seperateSecurityaccountCurrencySet, globalparametersJpaRepository.getCurrencyPrecision());

  }

  /**
   * Combine the result of Cashaccounts and Securityaccounts for all portfolios.
   *
   * @param portfolio
   * @param accountGroupMap
   * @param cashaccountPositionSummaryByCashaccountMap
   * @param cscr
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

  private void combineAndCalculateSummaries(final AccountPositionGroupSummary accountPositionGroupSummary,
      final CashaccountPositionSummary accountPositionSummary,
      final SecurityPositionCurrenyGroupSummary securityPositionCurrenyGroupSummary) {

    accountPositionSummary.setSecuritiesValue(securityPositionCurrenyGroupSummary);
    accountPositionGroupSummary.accountPositionSummaryList.add(accountPositionSummary);
  }

}

class AccessCashaccountPositionSummary {

  /**
   * Only used for a cash transfer that happened in a connected transaction
   * without the main currency. It contains the id of transaction as key and
   * exchange rate as value.
   */
  public Map<Integer, Double> exchangeRateConnectedTransactionMap = new HashMap<>();

  public Map<Cashaccount, CashaccountPositionSummary> cashaccountPositionSummaryByCashaccountMap;
  public Map<String, CashaccountPositionSummary> cashaccountPositionSummaryByCurrencyMap;
  private Integer idCounter = 0;
  private Map<String, Integer> currencyPrecisionMap;

  public AccessCashaccountPositionSummary(Map<String, Integer> currencyPrecisionMap) {
    this.currencyPrecisionMap = currencyPrecisionMap;
  }

  public void preparteForNewPortfolio() {
    cashaccountPositionSummaryByCashaccountMap = new HashMap<>();
    cashaccountPositionSummaryByCurrencyMap = new HashMap<>();
  }

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
   * Create and initialize CashaccountPositionSummary. It gets the currency
   * exchange rate in case when main currency differs from the cash account
   * currency.
   *
   * @param mainCurrency
   * @param cashaccount
   * @return
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
