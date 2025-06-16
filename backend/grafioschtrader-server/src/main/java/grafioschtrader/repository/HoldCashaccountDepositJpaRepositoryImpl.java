package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.HoldCashaccountDeposit;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.FromToCurrency;
import grafioschtrader.reportviews.FromToCurrencyWithDate;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository.CashaccountForeignExChangeRate;
import grafioschtrader.repository.helper.HoldingsHelper;
import grafioschtrader.types.TransactionType;

/**
 * Implementation of custom repository methods for managing cash account deposit holdings.
 * 
 * <p>
 * This class handles the creation and maintenance of deposit/withdrawal time-frame records that track external cash
 * transfers over time. The implementation responds to multiple types of changes that can affect deposit valuations:
 * </p>
 * 
 * <ul>
 * <li>Transaction changes (deposits and withdrawals)</li>
 * <li>Historical quote price changes for currency pairs</li>
 * <li>Currency changes at tenant or portfolio level</li>
 * </ul>
 * 
 * <p>
 * <strong>Multi-Currency Handling:</strong>
 * </p>
 * <p>
 * The implementation maintains deposit values in multiple currencies (account currency, portfolio currency, and tenant
 * currency) with historical exchange rate applications for accurate cross-currency analysis.
 * </p>
 * 
 * <p>
 * <strong>Time-Frame Management:</strong>
 * </p>
 * <p>
 * Deposit holdings are organized as time periods with start and end dates, creating a complete history of external cash
 * transfers for performance analysis.
 * </p>
 */
public class HoldCashaccountDepositJpaRepositoryImpl implements HoldCashaccountDepositJpaRepositoryCustom {

  @Autowired
  private HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Override
  @Transactional
  @Modifying
  public void createCashaccountDepositTimeFrameForAllTenant() {
    List<Tenant> tenants = tenantJpaRepository.findAll();
    tenants.forEach(this::createCashaccountDepositTimeFrameByTenant);
  }

  @Transactional
  @Modifying
  @Override
  public void createCashaccountDepositTimeFrameByTenant(Integer idTenant) {
    createCashaccountDepositTimeFrameByTenant(tenantJpaRepository.getReferenceById(idTenant));
  }

  /**
   * Creates complete deposit time-frames for a specific tenant entity.
   * 
   * <p>
   * This method performs a full rebuild of deposit holdings by:
   * </p>
   * <ul>
   * <li>Removing all existing deposit holdings for the tenant</li>
   * <li>Loading exchange rate data and currency conversion mappings</li>
   * <li>Processing all portfolios and their cash accounts</li>
   * <li>Creating time-period holdings for deposit/withdrawal transactions</li>
   * </ul>
   * 
   * @param tenant the tenant entity for which to rebuild deposit holdings
   */
  private void createCashaccountDepositTimeFrameByTenant(Tenant tenant) {
    holdCashaccountDepositJpaRepository.removeByIdTenant(tenant.getIdTenant());
    HoldDepositForTenant holdDepositForTenant = new HoldDepositForTenant();
    holdDepositForTenant.setTenant(tenant);
    holdDepositForTenant.loadDataForTenant(holdCashaccountDepositJpaRepository, currencypairJpaRepository);
    createCashaccountDepositTimeFrameForPortfolios(holdDepositForTenant);
  }

  @Override
  public void adjustCashaccountDepositOrWithdrawal(Transaction transaction1, Transaction transaction2) {
    HoldDepositForTenant holdDepositForTenant = new HoldDepositForTenant();
    holdDepositForTenant.setTenant(transaction1.getIdTenant(), tenantJpaRepository);
    holdDepositForTenant.loadDataForTenant(holdCashaccountDepositJpaRepository, currencypairJpaRepository);
    adjustCashaccountDepositOrWithdrawal(transaction1, holdDepositForTenant);
    if (transaction2 != null) {
      adjustCashaccountDepositOrWithdrawal(transaction2, holdDepositForTenant);
    }
  }

  @Override
  public void adjustBecauseOfHistoryquotePriceChanges() {
    List<Transaction> transactions = transactionJpaRepository.getTransactionWhyHistoryquoteYounger();
    if (!transactions.isEmpty()) {
      adjustBecauseOfHistoryquotePriceChanges(transactions);
    }
  }

  /**
   * Adjusts deposit holdings due to historical quote price changes.
   * 
   * <p>
   * This method handles the scenario where exchange rates have been updated with more recent historical data, requiring
   * recalculation of deposit values in different currencies. It processes transactions grouped by tenant and cash
   * account.
   * </p>
   * 
   * <p>
   * <strong>Processing Strategy:</strong>
   * </p>
   * <ul>
   * <li>Groups transactions by tenant and cash account</li>
   * <li>Uses previous holding records as starting points</li>
   * <li>Recalculates deposit values with updated exchange rates</li>
   * <li>Maintains time-frame continuity</li>
   * </ul>
   * 
   * @param transactions list of transactions affected by quote changes
   */
  private void adjustBecauseOfHistoryquotePriceChanges(List<Transaction> transactions) {
    HoldDepositForTenant holdDepositForTenant = new HoldDepositForTenant();
    holdDepositForTenant.loadData(holdCashaccountDepositJpaRepository, currencypairJpaRepository);
    List<HoldCashaccountDeposit> holdCashaccountList = new ArrayList<>();
    Map<Integer, List<Transaction>> transactionMap = transactions.stream()
        .collect(Collectors.groupingBy(Transaction::getIdTenant, Collectors.toList()));
    List<HoldCashaccountDeposit> prevHoldingList = holdCashaccountDepositJpaRepository.getPrevHoldingRecords();
    Map<Integer, HoldCashaccountDeposit> prevHoldingMap = prevHoldingList.stream()
        .collect(Collectors.toMap(hcd -> hcd.getHoldCashaccountKey().getIdSecuritycashAccount(), hcd -> hcd));

    List<Tenant> tenants = tenantJpaRepository.findAllById(transactionMap.keySet());

    for (Tenant tenant : tenants) {
      holdDepositForTenant.setTenant(tenant);
      Map<Integer, List<Transaction>> transactionCaAcMap = transactionMap.get(tenant.getIdTenant()).stream()
          .collect(Collectors.groupingBy(transaction -> transaction.getCashaccount().getIdSecuritycashAccount(),
              Collectors.toList()));
      for (Integer idCashaccount : transactionCaAcMap.keySet()) {
        List<Transaction> transactionCaAc = transactionCaAcMap.get(idCashaccount);
        Portfolio portfolio = transactionCaAc.getFirst().getCashaccount().getPortfolio();
        holdDepositForTenant.setAmounts(prevHoldingMap.get(idCashaccount));
        holdCashaccountList.addAll(calcDepositOnTransactionsOfCashaccount(transactionCaAc, portfolio.getIdPortfolio(),
            portfolio.getCurrency(), holdDepositForTenant, null));
      }
    }
    holdCashaccountDepositJpaRepository.saveAll(holdCashaccountList);
  }

  /**
   * Adjusts deposit holdings incrementally for a single transaction change.
   * 
   * <p>
   * This method provides efficient incremental updates by:
   * </p>
   * <ul>
   * <li>Finding the most recent valid holding before the transaction date</li>
   * <li>Removing holdings from the transaction date onward</li>
   * <li>Recalculating affected holdings using the existing baseline</li>
   * <li>Maintaining proper time-frame continuity</li>
   * </ul>
   * 
   * @param transaction          the transaction causing the adjustment
   * @param holdDepositForTenant context object with exchange rate data and accumulators
   */
  private void adjustCashaccountDepositOrWithdrawal(Transaction transaction,
      HoldDepositForTenant holdDepositForTenant) {

    HoldCashaccountDeposit youngestBeforeDate = holdCashaccountDepositJpaRepository.getLastBeforeDateByCashaccount(
        transaction.getCashaccount().getIdSecuritycashAccount(), transaction.getTransactionDate());

    List<Transaction> caTransactions = transaction.getCashaccount().getTransactionList().stream()
        .filter(t -> (youngestBeforeDate == null
            || t.getTransactionDate().isAfter(youngestBeforeDate.getHoldCashaccountKey().getFromHoldDate()))
            && (t.getTransactionType() == TransactionType.DEPOSIT
                || t.getTransactionType() == TransactionType.WITHDRAWAL))
        .sorted().collect(Collectors.toList());
    Portfolio portfolio = transaction.getCashaccount().getPortfolio();

    if (youngestBeforeDate != null) {
      holdCashaccountDepositJpaRepository
          .deleteByHoldCashaccountKey_IdSecuritycashAccountAndHoldCashaccountKey_fromHoldDateAfter(
              transaction.getCashaccount().getIdSecuritycashAccount(), youngestBeforeDate.getFromHoldDate());
      holdDepositForTenant.setAmounts(youngestBeforeDate);
    } else {
      holdCashaccountDepositJpaRepository
          .deleteByHoldCashaccountKey_IdSecuritycashAccount(transaction.getCashaccount().getIdSecuritycashAccount());
    }
    holdCashaccountDepositJpaRepository.saveAll(this.calcDepositOnTransactionsOfCashaccount(caTransactions,
        portfolio.getIdPortfolio(), portfolio.getCurrency(), holdDepositForTenant, youngestBeforeDate));

  }

  /**
   * Creates deposit time-frames for all portfolios within a tenant.
   * 
   * <p>
   * This method processes each portfolio and its cash accounts to create complete deposit holdings. It filters
   * transactions to include only deposits and withdrawals, then processes them chronologically.
   * </p>
   * 
   * @param holdDepositForTenant context object with tenant data and exchange rate mappings
   */
  private void createCashaccountDepositTimeFrameForPortfolios(HoldDepositForTenant holdDepositForTenant) {
    List<HoldCashaccountDeposit> holdCashaccountList = new ArrayList<>();
    for (final Portfolio portfolio : holdDepositForTenant.tenant.getPortfolioList()) {
      for (Cashaccount cashaccount : portfolio.getCashaccountList()) {
        holdDepositForTenant.resetAmounts();
        List<Transaction> transactionCaAcList = cashaccount.getTransactionList().stream()
            .filter(transaction -> transaction.getTransactionType() == TransactionType.DEPOSIT
                || transaction.getTransactionType() == TransactionType.WITHDRAWAL)
            .sorted().collect(Collectors.toList());
        holdCashaccountList.addAll(calcDepositOnTransactionsOfCashaccount(transactionCaAcList,
            portfolio.getIdPortfolio(), portfolio.getCurrency(), holdDepositForTenant, null));
      }
    }
    holdCashaccountDepositJpaRepository.saveAll(holdCashaccountList);
  }

  /**
   * Creates deposit holdings from deposit/withdrawal transactions for a single cash account.
   * 
   * <p>
   * This method processes transactions chronologically and:
   * </p>
   * <ul>
   * <li>Accumulates deposit amounts in account, portfolio, and tenant currencies</li>
   * <li>Applies historical exchange rates for accurate cross-currency conversion</li>
   * <li>Creates time-period holdings with proper start/end dates</li>
   * <li>Handles connected transactions (transfers between accounts)</li>
   * </ul>
   * 
   * <p>
   * <strong>Currency Conversion:</strong>
   * </p>
   * <p>
   * The method calculates deposit values in three currencies using historical exchange rates from the transaction
   * dates, ensuring accurate multi-currency analysis.
   * </p>
   * 
   * @param transactionCaAcList         all deposit and withdrawal transactions for a cash account, sorted by
   *                                    transaction time
   * @param idPortfolio                 the portfolio identifier
   * @param portfolioCurrency           the portfolio's base currency
   * @param holdDepositForTenant        context object with exchange rate data and accumulators
   * @param firstHoldCashaccountDeposit optional existing holding to include in results
   * @return list of deposit holdings created from the transactions
   */
  private List<HoldCashaccountDeposit> calcDepositOnTransactionsOfCashaccount(List<Transaction> transactionCaAcList,
      Integer idPortfolio, String portfolioCurrency, HoldDepositForTenant holdDepositForTenant,
      HoldCashaccountDeposit firstHoldCashaccountDeposit) {
    List<HoldCashaccountDeposit> holdCashaccountList = new ArrayList<>();
    if (firstHoldCashaccountDeposit != null) {
      holdCashaccountList.add(firstHoldCashaccountDeposit);
    }
    LocalDate toHoldDate = null;
    for (Transaction transaction : transactionCaAcList) {
      holdDepositForTenant.depositCashaccoutCurrency += transaction.getCashaccountAmount();
      holdDepositForTenant.depositTenantCurrency += DataBusinessHelper.calcDepositOnTransactionsOfCashaccount(
          transaction, holdDepositForTenant.fromToCurrencyWithDateMap, holdDepositForTenant.tenant.getCurrency(),
          holdDepositForTenant.exchangeRateConnectedTransactionMap,
          holdDepositForTenant.currencypairFromToCurrencyMap).amountMC;
      HoldCashaccountDeposit holdCashaccount = new HoldCashaccountDeposit(transaction.getIdTenant(), idPortfolio,
          transaction.getCashaccount().getIdSecuritycashAccount(), transaction.getTransactionDate(), toHoldDate,
          DataBusinessHelper.round(holdDepositForTenant.depositCashaccoutCurrency),
          DataBusinessHelper.round(holdDepositForTenant.depositTenantCurrency));
      if (!holdDepositForTenant.tenant.getCurrency().equals(portfolioCurrency)) {
        holdDepositForTenant.depositPortfolioCurrency += DataBusinessHelper.calcDepositOnTransactionsOfCashaccount(
            transaction, holdDepositForTenant.fromToCurrencyWithDateMap, portfolioCurrency,
            holdDepositForTenant.exchangeRateConnectedTransactionMap,
            holdDepositForTenant.currencypairFromToCurrencyMap).amountMC;
      } else {
        holdDepositForTenant.depositPortfolioCurrency = holdDepositForTenant.depositTenantCurrency;
      }
      holdCashaccount
          .setDepositPortfolioCurrency(DataBusinessHelper.round(holdDepositForTenant.depositPortfolioCurrency));
      holdCashaccount.setToHoldDate(toHoldDate);
      if (!holdCashaccountList.isEmpty()) {
        holdCashaccountList.getLast().setToHoldDate(transaction.getTransactionDate().minusDays(1));
      }
      holdCashaccountList.add(holdCashaccount);
    }

    return holdCashaccountList;
  }

  /**
   * Context class for managing deposit calculations and currency conversion data for a tenant.
   * 
   * <p>
   * This class serves as a data container and accumulator for deposit processing, maintaining exchange rate mappings,
   * running totals, and tenant context information.
   * </p>
   * 
   * <p>
   * <strong>Currency Handling:</strong>
   * </p>
   * <p>
   * The class maintains deposit amounts in three currencies (account, portfolio, tenant) and provides the exchange rate
   * data needed for accurate conversions using historical rates.
   * </p>
   * 
   * <p>
   * <strong>Exchange Rate Management:</strong>
   * </p>
   * <p>
   * Supports both date-specific exchange rates for historical accuracy and current rates for connected transactions,
   * ensuring proper currency conversion in all scenarios.
   * </p>
   */
  private static class HoldDepositForTenant {
    /** Running total of deposits in the cash account's native currency. */
    public double depositCashaccoutCurrency = 0.0;
    /** Running total of deposits in the portfolio's base currency. */
    public double depositPortfolioCurrency = 0.0;
    /** Running total of deposits in the tenant's base currency. */
    public double depositTenantCurrency = 0.0;
    /** Map of historical exchange rates by currency pair and date. */
    public Map<FromToCurrencyWithDate, Double> fromToCurrencyWithDateMap;
    /** Map of currency pairs available for conversion. */
    public Map<FromToCurrency, Currencypair> currencypairFromToCurrencyMap;

    /**
     * Only used for a cash transfer that happened in a connected transaction without the main currency. It contains the
     * id of transaction as key and exchange rate as value.
     */

    public Map<Integer, Double> exchangeRateConnectedTransactionMap;
    public Tenant tenant;

    public HoldDepositForTenant() {
    }

    public void setTenant(Integer idTenant, TenantJpaRepository tenantJpaRepository) {
      tenant = tenantJpaRepository.getReferenceById(idTenant);
      exchangeRateConnectedTransactionMap = new HashMap<>();
    }

    public void setTenant(Tenant tenant) {
      this.tenant = tenant;
      exchangeRateConnectedTransactionMap = new HashMap<>();
    }

    /**
     * Loads exchange rate data for a specific tenant's cash account operations.
     * 
     * <p>
     * This method builds maps of historical exchange rates and available currency pairs for the tenant's
     * deposit/withdrawal transactions.
     * </p>
     * 
     * @param holdCashaccountDepositJpaRepository repository for loading exchange rate data
     * @param currencypairJpaRepository           repository for loading currency pair mappings
     */
    void loadDataForTenant(HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository,
        CurrencypairJpaRepository currencypairJpaRepository) {
      fromToCurrencyWithDateMap = holdCashaccountDepositJpaRepository
          .getCashaccountForeignExChangeRateByIdTenant(tenant.getIdTenant()).stream()
          .collect(Collectors.toMap(
              cfecr -> new FromToCurrencyWithDate(cfecr.getFromCurrency(), cfecr.getToCurrency(), cfecr.getDate()),
              CashaccountForeignExChangeRate::getClose));
      currencypairFromToCurrencyMap = HoldingsHelper.getUsedCurrencypiarsByIdTenant(tenant.getIdTenant(),
          currencypairJpaRepository);
    }

    /**
     * Loads exchange rate data for all tenants (used during historical quote adjustments).
     * 
     * <p>
     * This method builds global maps of exchange rates and currency pairs when processing transactions across multiple
     * tenants due to quote changes.
     * </p>
     * 
     * @param holdCashaccountDepositJpaRepository repository for loading exchange rate data
     * @param currencypairJpaRepository           repository for loading currency pair mappings
     */
    void loadData(HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository,
        CurrencypairJpaRepository currencypairJpaRepository) {
      fromToCurrencyWithDateMap = holdCashaccountDepositJpaRepository.getCashaccountForeignExChangeRate().stream()
          .collect(Collectors.toMap(
              cfecr -> new FromToCurrencyWithDate(cfecr.getFromCurrency(), cfecr.getToCurrency(), cfecr.getDate()),
              CashaccountForeignExChangeRate::getClose));
      currencypairFromToCurrencyMap = HoldingsHelper.transformToCurrencypairMapWithFromCurrencyAsKey(
          currencypairJpaRepository.getHoldCashaccountOutDatetedCurrencypairs());
    }

    /**
     * Sets the deposit amounts from an existing holding record.
     * 
     * <p>
     * This method is used for incremental updates where processing continues from a known previous state rather than
     * starting from zero.
     * </p>
     * 
     * @param hcd the existing deposit holding to initialize from, or null to reset to zero
     */
    public void setAmounts(HoldCashaccountDeposit hcd) {
      if (hcd == null) {
        this.resetAmounts();
      } else {
        this.depositCashaccoutCurrency = hcd.getDeposit();
        this.depositPortfolioCurrency = hcd.getDepositPortfolioCurrency();
        this.depositTenantCurrency = hcd.getDepositTenantCurrency();
      }
    }

    /**
     * Resets all deposit amounts to zero.
     * 
     * <p>
     * Used when starting fresh processing for a new cash account or when no previous holding baseline exists.
     * </p>
     */
    public void resetAmounts() {
      depositCashaccoutCurrency = 0.0;
      depositPortfolioCurrency = 0.0;
      depositTenantCurrency = 0.0;

    }
  }

}
