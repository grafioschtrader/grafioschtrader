
package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.HoldCashaccountBalance;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.FromToCurrency;
import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository.CashaccountBalanceChangeTransaction;
import grafioschtrader.repository.helper.HoldingsHelper;

/**
 * It changes with every new or updated transaction.
 *
 *
 */
public class HoldCashaccountBalanceJpaRepositoryImpl implements HoldCashaccountBalanceJpaRepositoryCustom {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  HoldCashaccountBalanceJpaRepository holdCashaccountBalanceJpaRepository;

  @Autowired
  CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  TenantJpaRepository tenantJpaRepository;

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
    long startTime = System.currentTimeMillis();
    createCashaccountBalanceEntireByTenant(tenantJpaRepository.getReferenceById(idTenant));
    log.debug("End - HoldCashaccountSaldo: {}", System.currentTimeMillis() - startTime);
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

  private HoldCashaccountBalance getHoldCashaccountBalance(Tenant tenant, CashaccountBalanceChangeTransaction csct,
      CashaccountSum cashaccountSum, Map<FromToCurrency, Currencypair> currencypairFromToCurrencyMap) {
    Integer idCurrencyTenant = null;
    Integer idCurrencyPortfolio = null;
    if (!tenant.getCurrency().equals(csct.getAccountCurrency())) {
      idCurrencyTenant = getCurrencypair(currencypairFromToCurrencyMap, csct.getAccountCurrency(),
          tenant.getCurrency());
    }
    if (!csct.getPortfolioCurrency().equals(csct.getAccountCurrency())) {
      idCurrencyPortfolio = getCurrencypair(currencypairFromToCurrencyMap, csct.getAccountCurrency(),
          csct.getPortfolioCurrency());
    }
    cashaccountSum.cashBalance += csct.getTotal();
    cashaccountSum.accumulateReduce += csct.getAccumulateReduce();
    cashaccountSum.dividend += csct.getDividend();
    cashaccountSum.withdrawlDeposit += csct.getWithdrawlDeposit();
    cashaccountSum.interestCashaccount += csct.getInterestCashaccount();
    cashaccountSum.fee += csct.getFee();
    return new HoldCashaccountBalance(tenant.getIdTenant(), csct.getIdPortfolio(), csct.getIdCashaccount(),
        csct.getFromDate(), cashaccountSum.withdrawlDeposit, cashaccountSum.interestCashaccount, cashaccountSum.fee,
        cashaccountSum.accumulateReduce, cashaccountSum.dividend,
        DataHelper.round(cashaccountSum.cashBalance, GlobalConstants.FID_STANDARD_FRACTION_DIGITS), idCurrencyTenant,
        idCurrencyPortfolio);
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
