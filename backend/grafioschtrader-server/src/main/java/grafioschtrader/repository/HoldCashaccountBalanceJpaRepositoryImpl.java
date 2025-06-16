
package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.common.DataHelper;
import grafiosch.common.DateHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.HoldCashaccountBalance;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.FromToCurrency;
import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository.CashaccountBalanceChangeTransaction;
import grafioschtrader.repository.helper.HoldingsHelper;
import grafioschtrader.service.GlobalparametersService;

/**
 * Implementation of custom repository methods for managing cash account balance holdings.
 * 
 * <p>
 * This class handles the creation and maintenance of time-based cash account balance records that track all
 * cash-affecting transactions over time. The implementation is designed to respond to transaction changes and maintain
 * accurate balance histories for performance analysis and reporting.
 * </p>
 * 
 * <p>
 * <strong>Transaction Impact:</strong>
 * </p>
 * <p>
 * Balance holdings are automatically updated whenever transactions are added, modified, or removed, ensuring that
 * historical balance data remains accurate for analysis.
 * </p>
 * 
 * <p>
 * <strong>Multi-Currency Processing:</strong>
 * </p>
 * <p>
 * The implementation handles currency conversion for accounts with different currencies than their portfolio or tenant
 * base currencies, maintaining conversion references for accurate cross-currency reporting.
 * </p>
 */
public class HoldCashaccountBalanceJpaRepositoryImpl implements HoldCashaccountBalanceJpaRepositoryCustom {

  @Autowired
  private HoldCashaccountBalanceJpaRepository holdCashaccountBalanceJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Override
  @Transactional
  @Modifying
  public void createCashaccountBalanceEntireForAllTenants() {
    List<Tenant> tenants = tenantJpaRepository.findAll();
    tenants.forEach(this::createCashaccountBalanceEntireByTenant);
  }

  @Override
  @Transactional
  @Modifying
  public void createCashaccountBalanceEntireByTenant(Integer idTenant) {
    createCashaccountBalanceEntireByTenant(tenantJpaRepository.getReferenceById(idTenant));
  }

  /**
   * Creates complete cash account balance holdings for a specific tenant entity.
   * 
   * <p>
   * This method performs a full rebuild of balance holdings by:
   * </p>
   * <ul>
   * <li>Removing all existing holdings for the tenant</li>
   * <li>Loading currency conversion mappings</li>
   * <li>Processing all cash account balance change transactions</li>
   * <li>Creating time-period holdings with proper start/end dates</li>
   * <li>Accumulating balances across transaction types</li>
   * </ul>
   * 
   * <p>
   * <strong>Time Period Management:</strong>
   * </p>
   * <p>
   * The method creates holding periods by setting end dates on previous periods when new transactions occur, ensuring
   * continuous coverage without gaps.
   * </p>
   * 
   * @param tenant the tenant entity for which to rebuild holdings
   */
  private void createCashaccountBalanceEntireByTenant(Tenant tenant) {
    holdCashaccountBalanceJpaRepository.removeByIdTenant(tenant.getIdTenant());
    Map<FromToCurrency, Currencypair> currencypairFromToCurrencyMap = HoldingsHelper
        .getUsedCurrencypiarsByIdTenant(tenant.getIdTenant(), currencypairJpaRepository);
    List<CashaccountBalanceChangeTransaction> cashaccountSaldoChangeTransactionList = holdCashaccountBalanceJpaRepository
        .getCashaccountBalanceByTenant(tenant.getIdTenant());

    List<HoldCashaccountBalance> holdCashaccountBalanceList = new ArrayList<>();
    Integer lastIdCashaccount = null;
    CashaccountSum cashaccountSum = new CashaccountSum();
    for (CashaccountBalanceChangeTransaction csct : cashaccountSaldoChangeTransactionList) {
      if (lastIdCashaccount != null && lastIdCashaccount.equals(csct.getIdCashaccount())) {
        holdCashaccountBalanceList.get(holdCashaccountBalanceList.size() - 1)
            .setToHoldDate(csct.getFromDate().minusDays(1));
      } else {
        // Cash account has changed
        lastIdCashaccount = csct.getIdCashaccount();
        cashaccountSum = new CashaccountSum();
      }
      holdCashaccountBalanceList
          .add(getHoldCashaccountBalance(tenant, csct, cashaccountSum, currencypairFromToCurrencyMap));
    }
    holdCashaccountBalanceJpaRepository.saveAll(holdCashaccountBalanceList);

  }

  @Override
  public void adjustCashaccountBalanceByIdCashaccountAndFromDate(Transaction transaction) {
    List<HoldCashaccountBalance> holdCashaccountBalanceList = new ArrayList<>();
    Integer idCashaccount = transaction.getCashaccount().getIdSecuritycashAccount();
    LocalDate transactionDate = DateHelper.getLocalDate(transaction.getTransactionTime());

    Tenant tenant = tenantJpaRepository.getReferenceById(transaction.getIdTenant());
    holdCashaccountBalanceJpaRepository.removeByIdTenantAndIdEmIdSecuritycashAccountAndIdEmFromHoldDateGreaterThanEqual(
        transaction.getIdTenant(), idCashaccount, transactionDate);

    HoldCashaccountBalance youngestValidBalance = holdCashaccountBalanceJpaRepository
        .getCashaccountBalanceMaxFromDateByCashaccount(idCashaccount);
    if (youngestValidBalance != null) {
      holdCashaccountBalanceList.add(youngestValidBalance);
      youngestValidBalance.setToHoldDate(null);
    }

    Map<FromToCurrency, Currencypair> currencypairFromToCurrencyMap = HoldingsHelper
        .getUsedCurrencypiarsByIdTenant(transaction.getIdTenant(), currencypairJpaRepository);
    List<CashaccountBalanceChangeTransaction> cashaccountBalanceChangeTransactionList = holdCashaccountBalanceJpaRepository
        .getCashaccountBalanceByCashaccountAndDate(idCashaccount, transactionDate);
    CashaccountSum cashaccountSum = new CashaccountSum(youngestValidBalance);
    cashaccountBalanceChangeTransactionList.forEach(cbct -> {
      if (!holdCashaccountBalanceList.isEmpty()) {
        holdCashaccountBalanceList.get(holdCashaccountBalanceList.size() - 1)
            .setToHoldDate(cbct.getFromDate().minusDays(1));
      }
      holdCashaccountBalanceList
          .add(getHoldCashaccountBalance(tenant, cbct, cashaccountSum, currencypairFromToCurrencyMap));

    });

    holdCashaccountBalanceJpaRepository.saveAll(holdCashaccountBalanceList);
  }

  /**
   * Creates a cash account balance holding record from transaction data and accumulated sums.
   * 
   * <p>
   * This method handles the core logic for building balance holdings including:
   * </p>
   * <ul>
   * <li>Currency conversion setup for tenant and portfolio currencies</li>
   * <li>Accumulation of various transaction types</li>
   * <li>Precision rounding based on account currency</li>
   * <li>Creation of holding entity with proper currency references</li>
   * </ul>
   * 
   * <p>
   * <strong>Currency Conversion:</strong>
   * </p>
   * <p>
   * If the account currency differs from tenant or portfolio currencies, the method identifies and stores the
   * appropriate currency pair references for later conversion during analysis.
   * </p>
   * 
   * @param tenant                        the tenant context for currency and entity references
   * @param cbct                          the balance change transaction containing daily aggregated data
   * @param cashaccountSum                the running accumulator for balance components
   * @param currencypairFromToCurrencyMap map of available currency pairs for conversion
   * @return a new HoldCashaccountBalance entity for the transaction date
   */
  private HoldCashaccountBalance getHoldCashaccountBalance(Tenant tenant, CashaccountBalanceChangeTransaction cbct,
      CashaccountSum cashaccountSum, Map<FromToCurrency, Currencypair> currencypairFromToCurrencyMap) {
    Integer idCurrencyTenant = null;
    Integer idCurrencyPortfolio = null;
    if (!tenant.getCurrency().equals(cbct.getAccountCurrency())) {
      idCurrencyTenant = getCurrencypair(currencypairFromToCurrencyMap, cbct.getAccountCurrency(),
          tenant.getCurrency());
    }
    if (!cbct.getPortfolioCurrency().equals(cbct.getAccountCurrency())) {
      idCurrencyPortfolio = getCurrencypair(currencypairFromToCurrencyMap, cbct.getAccountCurrency(),
          cbct.getPortfolioCurrency());
    }
    cashaccountSum.cashBalance += cbct.getTotal();
    cashaccountSum.accumulateReduce += cbct.getAccumulateReduce();
    cashaccountSum.dividend += cbct.getDividend();
    cashaccountSum.withdrawlDeposit += cbct.getWithdrawlDeposit();
    cashaccountSum.interestCashaccount += cbct.getInterestCashaccount();
    cashaccountSum.fee += cbct.getFee();

    int precision = globalparametersService.getPrecisionForCurrency(cbct.getAccountCurrency());
    return new HoldCashaccountBalance(tenant.getIdTenant(), cbct.getIdPortfolio(), cbct.getIdCashaccount(),
        cbct.getFromDate(), cashaccountSum.withdrawlDeposit, cashaccountSum.interestCashaccount, cashaccountSum.fee,
        cashaccountSum.accumulateReduce, cashaccountSum.dividend,
        DataHelper.round(cashaccountSum.cashBalance, precision), idCurrencyTenant, idCurrencyPortfolio);
  }

  /**
   * Retrieves the currency pair ID for conversion between two currencies.
   * 
   * <p>
   * This method looks up or creates the necessary currency pair for converting from the account currency to the target
   * currency (tenant or portfolio currency).
   * </p>
   * 
   * @param currencypairFromToCurrencyMap map of available currency pairs
   * @param fromCurrency                  the source currency (account currency)
   * @param toCurrency                    the target currency (tenant or portfolio currency)
   * @return the currency pair ID for conversion reference
   */
  private Integer getCurrencypair(Map<FromToCurrency, Currencypair> currencypairFromToCurrencyMap, String fromCurrency,
      String toCurrency) {
    Currencypair currencypair = HoldingsHelper.getCurrency(currencypairJpaRepository, currencypairFromToCurrencyMap,
        fromCurrency, toCurrency);
    return currencypair.getIdSecuritycurrency();

  }

  /**
   * Accumulator class for tracking running totals of different transaction types.
   * 
   * <p>
   * This class maintains running sums of various cash account components as transactions are processed chronologically.
   * It supports initialization from existing balance holdings for incremental updates.
   * </p>
   */
  static class CashaccountSum {
    /** Running total of cash account balance. */
    public double cashBalance = 0.0;
    /** Running total of accumulate/reduce transactions (security buys/sells). */
    public double accumulateReduce = 0.0;
    /** Running total of dividend payments. */
    public double dividend = 0.0;
    /** Running total of deposits and withdrawals. */
    public double withdrawlDeposit = 0.0;
    /** Running total of interest earned. */
    public double interestCashaccount = 0.0;
    /** Running total of fees charged. */
    public double fee = 0.0;

    public CashaccountSum() {
    }

    /**
     * Creates a new accumulator initialized from an existing balance holding.
     * 
     * <p>
     * This constructor is used for incremental updates where processing continues from a known previous state rather
     * than starting from zero.
     * </p>
     * 
     * @param hcb the existing balance holding to initialize from, or null for zero values
     */
    public CashaccountSum(HoldCashaccountBalance hcb) {
      if (hcb != null) {
        cashBalance = hcb.getBalance();
        accumulateReduce = hcb.getAccumulateReduce();
        dividend = hcb.getDividend();
        withdrawlDeposit = hcb.getWithdrawlDeposit();
        interestCashaccount = hcb.getInterestCashaccount();
        fee = hcb.getFee();
      }
    }

  }

}
