
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
 * It changes with every new or updated transaction.
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
  // @Transactional
  // @Modifying
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

  private Integer getCurrencypair(Map<FromToCurrency, Currencypair> currencypairFromToCurrencyMap, String fromCurrency,
      String toCurrency) {
    Currencypair currencypair = HoldingsHelper.getCurrency(currencypairJpaRepository, currencypairFromToCurrencyMap,
        fromCurrency, toCurrency);
    return currencypair.getIdSecuritycurrency();

  }

  static class CashaccountSum {
    public double cashBalance = 0.0;
    public double accumulateReduce = 0.0;
    public double dividend = 0.0;
    public double withdrawlDeposit = 0.0;
    public double interestCashaccount = 0.0;
    public double fee = 0.0;

    public CashaccountSum() {
    }

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
