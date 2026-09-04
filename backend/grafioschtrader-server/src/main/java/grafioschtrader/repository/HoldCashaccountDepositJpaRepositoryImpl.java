package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.common.DataBusinessHelper.CashaccountTransfer;
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
import grafioschtrader.repository.helper.TransactionPreImage;
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
    holdDepositForTenant.loadDataForTenant(holdCashaccountDepositJpaRepository, currencypairJpaRepository,
        transactionJpaRepository);
    createCashaccountDepositTimeFrameForPortfolios(holdDepositForTenant);
  }

  @Override
  public void adjustCashaccountDepositOrWithdrawal(Transaction transaction1, TransactionPreImage preImage1,
      Transaction transaction2, TransactionPreImage preImage2) {
    HoldDepositForTenant holdDepositForTenant = new HoldDepositForTenant();
    holdDepositForTenant.setTenant(transaction1.getIdTenant(), tenantJpaRepository);
    holdDepositForTenant.loadDataForTenant(holdCashaccountDepositJpaRepository, currencypairJpaRepository,
        transactionJpaRepository);
    holdDepositForTenant.registerConnectedTransactions(transaction1, transaction2);
    adjustCashaccountDepositOrWithdrawal(transaction1, preImage1, holdDepositForTenant);
    if (transaction2 != null) {
      adjustCashaccountDepositOrWithdrawal(transaction2, preImage2, holdDepositForTenant);
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
    Map<Integer, List<Transaction>> transactionMap = transactions.stream()
        .collect(Collectors.groupingBy(Transaction::getIdTenant, Collectors.toList()));
    HoldDepositForTenant holdDepositForTenant = new HoldDepositForTenant();
    holdDepositForTenant.loadData(holdCashaccountDepositJpaRepository, currencypairJpaRepository,
        transactionJpaRepository, transactionMap.keySet());
    List<HoldCashaccountDeposit> holdCashaccountList = new ArrayList<>();
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
   * A transaction that moved to another cash account, or backwards in time, invalidates rows the new coordinates alone
   * cannot reach, so the former account and the former date are replayed as well. Both series are cumulative running
   * totals, which is why the replay always has to start at the earlier of the two dates.
   * </p>
   *
   * @param transaction          the transaction causing the adjustment
   * @param preImage             where the transaction was booked before the update, or {@code null} for a create or a
   *                             delete
   * @param holdDepositForTenant context object with exchange rate data and accumulators
   */
  private void adjustCashaccountDepositOrWithdrawal(Transaction transaction, TransactionPreImage preImage,
      HoldDepositForTenant holdDepositForTenant) {
    Integer idCashaccount = transaction.getCashaccount().getIdSecuritycashAccount();
    LocalDate transactionDate = transaction.getTransactionDate();

    if (preImage == null) {
      replayDepositFrom(idCashaccount, transactionDate, holdDepositForTenant);
    } else if (preImage.cashaccountChanged(idCashaccount)) {
      replayDepositFrom(preImage.idCashaccount(), preImage.transactionDate(), holdDepositForTenant);
      replayDepositFrom(idCashaccount, transactionDate, holdDepositForTenant);
    } else {
      replayDepositFrom(idCashaccount, preImage.earliestAffectedDate(transactionDate), holdDepositForTenant);
    }
  }

  /**
   * Deletes the deposit hold rows of one cash account after the youngest row that is still valid at {@code fromDate}
   * and rebuilds them by replaying the account's deposits and withdrawals.
   *
   * <p>
   * The transactions are read from the database rather than from {@code cashaccount.getTransactionList()}: that
   * collection may already have been initialised earlier in the same persistence context and would then still contain a
   * transaction that has just been deleted, or miss one that has just been inserted.
   * </p>
   *
   * @param idCashaccount        the cash account to replay
   * @param fromDate             the first date whose rows are no longer trusted
   * @param holdDepositForTenant context object with exchange rate data and accumulators
   */
  private void replayDepositFrom(Integer idCashaccount, LocalDate fromDate, HoldDepositForTenant holdDepositForTenant) {
    HoldCashaccountDeposit youngestBeforeDate = holdCashaccountDepositJpaRepository
        .getLastBeforeDateByCashaccount(idCashaccount, fromDate);

    List<Transaction> caTransactions;
    if (youngestBeforeDate != null) {
      holdCashaccountDepositJpaRepository
          .deleteByHoldCashaccountKey_IdSecuritycashAccountAndHoldCashaccountKey_fromHoldDateAfter(idCashaccount,
              youngestBeforeDate.getFromHoldDate());
      // The seed becomes the open period again; without this it would keep the end date of a period that no longer
      // exists and every report would stop reading the account there.
      youngestBeforeDate.setToHoldDate(null);
      holdDepositForTenant.setAmounts(youngestBeforeDate);
      caTransactions = transactionJpaRepository.findDepositWithdrawalByCashaccountAfterDate(idCashaccount,
          youngestBeforeDate.getFromHoldDate());
    } else {
      holdCashaccountDepositJpaRepository.deleteByHoldCashaccountKey_IdSecuritycashAccount(idCashaccount);
      holdDepositForTenant.resetAmounts();
      caTransactions = transactionJpaRepository.findDepositWithdrawalByCashaccount(idCashaccount);
    }

    if (caTransactions.isEmpty()) {
      if (youngestBeforeDate != null) {
        holdCashaccountDepositJpaRepository.save(youngestBeforeDate);
      }
      return;
    }
    Portfolio portfolio = caTransactions.getFirst().getCashaccount().getPortfolio();
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
      holdDepositForTenant.depositTenantCurrency += holdDepositForTenant
          .calcDepositOnTransactionsOfCashaccount(transaction, holdDepositForTenant.tenant.getCurrency()).amountMC;
      HoldCashaccountDeposit holdCashaccount = new HoldCashaccountDeposit(transaction.getIdTenant(), idPortfolio,
          transaction.getCashaccount().getIdSecuritycashAccount(), transaction.getTransactionDate(), toHoldDate,
          holdDepositForTenant.depositCashaccoutCurrency, holdDepositForTenant.depositTenantCurrency);
      if (!holdDepositForTenant.tenant.getCurrency().equals(portfolioCurrency)) {
        holdDepositForTenant.depositPortfolioCurrency += holdDepositForTenant
            .calcDepositOnTransactionsOfCashaccount(transaction, portfolioCurrency).amountMC;
      } else {
        holdDepositForTenant.depositPortfolioCurrency = holdDepositForTenant.depositTenantCurrency;
      }
      holdCashaccount.setDepositPortfolioCurrency(holdDepositForTenant.depositPortfolioCurrency);
      holdCashaccount.setToHoldDate(toHoldDate);
      if (!holdCashaccountList.isEmpty()) {
        holdCashaccountList.getLast().setToHoldDate(transaction.getTransactionDate().minusDays(1));
      }
      holdCashaccountList.add(holdCashaccount);
    }

    return holdCashaccountList;
  }

  /**
   * Converts one side of a connected cash transfer independently of processing order. When the target currency is one
   * side's account currency, that booked amount is authoritative. Otherwise the withdrawal is converted with the
   * existing historical-rate rules. The other side receives the exact opposite amount.
   *
   * @param transaction                   transaction being accumulated
   * @param counterpart                   connected transaction on the other cash account
   * @param mainCurrency                  target currency
   * @param fromToCurrencyWithDateMap     historical exchange rates
   * @param currencypairFromToCurrencyMap current exchange rates used by the existing fallback
   * @return the transaction amount converted to the target currency
   */
  static CashaccountTransfer calculateConnectedTransferAmount(Transaction transaction, Transaction counterpart,
      String mainCurrency, Map<FromToCurrencyWithDate, Double> fromToCurrencyWithDateMap,
      Map<FromToCurrency, Currencypair> currencypairFromToCurrencyMap) {
    Transaction withdrawal = transaction.getTransactionType() == TransactionType.WITHDRAWAL ? transaction : counterpart;
    Transaction deposit = transaction.getTransactionType() == TransactionType.DEPOSIT ? transaction : counterpart;
    if (withdrawal.getTransactionType() != TransactionType.WITHDRAWAL
        || deposit.getTransactionType() != TransactionType.DEPOSIT) {
      throw new IllegalStateException("Connected cash transactions " + transaction.getIdTransaction() + " and "
          + counterpart.getIdTransaction() + " are not a withdrawal/deposit pair");
    }

    Transaction authoritative = mainCurrency.equals(deposit.getCashaccount().getCurrency()) ? deposit : withdrawal;
    CashaccountTransfer authoritativeAmount = DataBusinessHelper.calcDepositOnTransactionsOfCashaccount(authoritative,
        fromToCurrencyWithDateMap, mainCurrency, new HashMap<>(), currencypairFromToCurrencyMap);
    if (transaction == authoritative || transaction.getIdTransaction().equals(authoritative.getIdTransaction())) {
      return authoritativeAmount;
    }
    CashaccountTransfer oppositeAmount = new CashaccountTransfer();
    oppositeAmount.amountMC = -authoritativeAmount.amountMC;
    return oppositeAmount;
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

    /** Both sides of connected cash transfers, keyed by transaction id. */
    private Map<Integer, Transaction> connectedCashTransferById = new HashMap<>();
    public Tenant tenant;

    public HoldDepositForTenant() {
    }

    public void setTenant(Integer idTenant, TenantJpaRepository tenantJpaRepository) {
      tenant = tenantJpaRepository.getReferenceById(idTenant);
    }

    public void setTenant(Tenant tenant) {
      this.tenant = tenant;
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
     * @param transactionJpaRepository            repository for loading both sides of connected transfers
     */
    void loadDataForTenant(HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository,
        CurrencypairJpaRepository currencypairJpaRepository, TransactionJpaRepository transactionJpaRepository) {
      fromToCurrencyWithDateMap = holdCashaccountDepositJpaRepository
          .getCashaccountForeignExChangeRateByIdTenant(tenant.getIdTenant()).stream()
          .collect(Collectors.toMap(
              cfecr -> new FromToCurrencyWithDate(cfecr.getFromCurrency(), cfecr.getToCurrency(), cfecr.getDate()),
              CashaccountForeignExChangeRate::getClose));
      currencypairFromToCurrencyMap = HoldingsHelper.getUsedCurrencypiarsByIdTenant(tenant.getIdTenant(),
          currencypairJpaRepository);
      loadConnectedTransactions(transactionJpaRepository, Set.of(tenant.getIdTenant()));
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
     * @param transactionJpaRepository            repository for loading both sides of connected transfers
     * @param idTenants                           tenants affected by the historical quote changes
     */
    void loadData(HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository,
        CurrencypairJpaRepository currencypairJpaRepository, TransactionJpaRepository transactionJpaRepository,
        Set<Integer> idTenants) {
      fromToCurrencyWithDateMap = holdCashaccountDepositJpaRepository.getCashaccountForeignExChangeRate().stream()
          .collect(Collectors.toMap(
              cfecr -> new FromToCurrencyWithDate(cfecr.getFromCurrency(), cfecr.getToCurrency(), cfecr.getDate()),
              CashaccountForeignExChangeRate::getClose));
      currencypairFromToCurrencyMap = HoldingsHelper.transformToCurrencypairMapWithFromCurrencyAsKey(
          currencypairJpaRepository.getHoldCashaccountOutDatetedCurrencypairs());
      loadConnectedTransactions(transactionJpaRepository, idTenants);
    }

    /**
     * Adds the connected transactions supplied by a write operation. This is required for deletes, where both rows have
     * already been removed before the hold-table replay begins.
     *
     * @param transaction1 first transaction, never {@code null}
     * @param transaction2 optional connected transaction
     */
    void registerConnectedTransactions(Transaction transaction1, Transaction transaction2) {
      registerConnectedTransaction(transaction1);
      registerConnectedTransaction(transaction2);
    }

    private void loadConnectedTransactions(TransactionJpaRepository transactionJpaRepository, Set<Integer> idTenants) {
      connectedCashTransferById = transactionJpaRepository.findConnectedCashTransfersByIdTenantIn(idTenants).stream()
          .collect(Collectors.toMap(Transaction::getIdTransaction, transaction -> transaction));
    }

    private void registerConnectedTransaction(Transaction transaction) {
      if (transaction != null && transaction.isCashaccountTransfer()) {
        connectedCashTransferById.put(transaction.getIdTransaction(), transaction);
      }
    }

    /**
     * Converts a deposit or withdrawal without depending on which side of a connected transfer was encountered first.
     * The side denominated in the requested main currency is authoritative; when neither side uses it, the withdrawal
     * is the stable fallback. The opposite side receives the exact negated amount.
     *
     * @param transaction  the deposit or withdrawal to convert
     * @param mainCurrency target currency
     * @return converted amount and transaction fee
     */
    CashaccountTransfer calcDepositOnTransactionsOfCashaccount(Transaction transaction, String mainCurrency) {
      if (!transaction.isCashaccountTransfer()) {
        return DataBusinessHelper.calcDepositOnTransactionsOfCashaccount(transaction, fromToCurrencyWithDateMap,
            mainCurrency, Collections.emptyMap(), currencypairFromToCurrencyMap);
      }

      Transaction counterpart = connectedCashTransferById.get(transaction.getConnectedIdTransaction());
      if (counterpart == null) {
        throw new IllegalStateException("Connected cash transaction " + transaction.getConnectedIdTransaction()
            + " is missing for transaction " + transaction.getIdTransaction());
      }
      return calculateConnectedTransferAmount(transaction, counterpart, mainCurrency, fromToCurrencyWithDateMap,
          currencypairFromToCurrencyMap);
    }

    /**
     * Sets the deposit amounts from an existing holding record.
     *
     * <p>
     * This method is used for incremental updates where processing continues from the previous state rather than
     * starting from zero. The three columns are stored unrounded, so the seed is the exact running total a full rebuild
     * would hold at that point and both paths produce the same values from here on. Rounding them on write would
     * re-enter the accumulators here and offset the whole remainder of the series against a rebuild.
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
