package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.common.DataHelper;
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
 * It can be affected by changes of transaction (Deposit, Withdrawal) and
 * changed close price of history quotes of currency pairs. It changes also when
 * currency of the tenant or a portfolio changes.
 *
 */
public class HoldCashaccountDepositJpaRepositoryImpl implements HoldCashaccountDepositJpaRepositoryCustom {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository;

  @Autowired
  TenantJpaRepository tenantJpaRepository;

  @Autowired
  CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  TransactionJpaRepository transactionJpaRepository;

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
    long startTime = System.currentTimeMillis();
    createCashaccountDepositTimeFrameByTenant(tenantJpaRepository.getReferenceById(idTenant));
    log.debug("End - HoldCashaccountDeposit: {}", System.currentTimeMillis() - startTime);
  }

  public void createCashaccountDepositTimeFrameByTenant(Tenant tenant) {
    long startTime = System.currentTimeMillis();
    holdCashaccountDepositJpaRepository.removeByIdTenant(tenant.getIdTenant());
    HoldDepositForTenant holdDepositForTenant = new HoldDepositForTenant();
    holdDepositForTenant.setTenant(tenant);
    holdDepositForTenant.loadDataForTenant(holdCashaccountDepositJpaRepository, currencypairJpaRepository);
    createCashaccountDepositTimeFrameForPortfolios(holdDepositForTenant);
    log.debug("End - HoldCashaccountDeposit: {}", System.currentTimeMillis() - startTime);
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
        Portfolio portfolio = transactionCaAc.get(0).getCashaccount().getPortfolio();
        holdDepositForTenant.setAmounts(prevHoldingMap.get(idCashaccount));
        holdCashaccountList.addAll(calcDepositOnTransactionsOfCashaccount(transactionCaAc, portfolio.getIdPortfolio(),
            portfolio.getCurrency(), holdDepositForTenant, null));
      }
    }
    holdCashaccountDepositJpaRepository.saveAll(holdCashaccountList);

  }

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
   * Creates HoldCashaccountDeposit from transactions for a single cash account.
   *
   *
   * @param transactionCaAcList         All deposit and withdrawal transaction of
   *                                    a portfolios cash accounts sorted by
   *                                    transaction time (ASC)
   * @param idPortfolio
   * @param portfolioCurrency
   * @param holdDepositForTenant
   * @param firstHoldCashaccountDeposit
   * @return
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
      holdDepositForTenant.depositTenantCurrency += DataHelper.calcDepositOnTransactionsOfCashaccount(transaction,
          holdDepositForTenant.fromToCurrencyWithDateMap, holdDepositForTenant.tenant.getCurrency(),
          holdDepositForTenant.exchangeRateConnectedTransactionMap,
          holdDepositForTenant.currencypairFromToCurrencyMap).amountMC;
      HoldCashaccountDeposit holdCashaccount = new HoldCashaccountDeposit(transaction.getIdTenant(), idPortfolio,
          transaction.getCashaccount().getIdSecuritycashAccount(), transaction.getTransactionDate(), toHoldDate,
          DataHelper.round(holdDepositForTenant.depositCashaccoutCurrency),
          DataHelper.round(holdDepositForTenant.depositTenantCurrency));
      if (!holdDepositForTenant.tenant.getCurrency().equals(portfolioCurrency)) {
        holdDepositForTenant.depositPortfolioCurrency += DataHelper.calcDepositOnTransactionsOfCashaccount(transaction,
            holdDepositForTenant.fromToCurrencyWithDateMap, portfolioCurrency,
            holdDepositForTenant.exchangeRateConnectedTransactionMap,
            holdDepositForTenant.currencypairFromToCurrencyMap).amountMC;
      } else {
        holdDepositForTenant.depositPortfolioCurrency = holdDepositForTenant.depositTenantCurrency;
      }
      holdCashaccount.setDepositPortfolioCurrency(DataHelper.round(holdDepositForTenant.depositPortfolioCurrency));
      holdCashaccount.setToHoldDate(toHoldDate);
      if (!holdCashaccountList.isEmpty()) {
        holdCashaccountList.get(holdCashaccountList.size() - 1)
            .setToHoldDate(transaction.getTransactionDate().minusDays(1));
      }
      holdCashaccountList.add(holdCashaccount);
    }

    return holdCashaccountList;
  }

  private static class HoldDepositForTenant {
    public double depositCashaccoutCurrency = 0.0;
    public double depositPortfolioCurrency = 0.0;
    public double depositTenantCurrency = 0.0;

    public Map<FromToCurrencyWithDate, Double> fromToCurrencyWithDateMap;
    public Map<FromToCurrency, Currencypair> currencypairFromToCurrencyMap;

    /**
     * Only used for a cash transfer that happened in a connected transaction
     * without the main currency. It contains the id of transaction as key and
     * exchange rate as value.
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

    void loadData(HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository,
        CurrencypairJpaRepository currencypairJpaRepository) {
      fromToCurrencyWithDateMap = holdCashaccountDepositJpaRepository.getCashaccountForeignExChangeRate().stream()
          .collect(Collectors.toMap(
              cfecr -> new FromToCurrencyWithDate(cfecr.getFromCurrency(), cfecr.getToCurrency(), cfecr.getDate()),
              CashaccountForeignExChangeRate::getClose));
      currencypairFromToCurrencyMap = HoldingsHelper.transformToCurrencypairMapWithFromCurrencyAsKey(
          currencypairJpaRepository.getHoldCashaccountOutDatetedCurrencypairs());
    }

    public void setAmounts(HoldCashaccountDeposit hcd) {
      if (hcd == null) {
        this.resetAmounts();
      } else {
        this.depositCashaccoutCurrency = hcd.getDeposit();
        this.depositPortfolioCurrency = hcd.getDepositPortfolioCurrency();
        this.depositTenantCurrency = hcd.getDepositTenantCurrency();
      }
    }

    public void resetAmounts() {
      depositCashaccoutCurrency = 0.0;
      depositPortfolioCurrency = 0.0;
      depositTenantCurrency = 0.0;

    }
  }

}
