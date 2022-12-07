package grafioschtrader.reports;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.transactioncost.TransactionCostGrandSummary;
import grafioschtrader.reportviews.transactioncost.TransactionCostGroupSummary;
import grafioschtrader.reportviews.transactioncost.TransactionCostPosition;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
// import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
// import grafioschtrader.task.RepTreeWeka;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

@Component
public class SecurityTransactionCostReport {

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  public TransactionCostGrandSummary getTransactionCostGrandSummary(final Integer idTenant) {
    final Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);

    final CompletableFuture<List<Transaction>> cfTransactionJpaRepository = CompletableFuture
        .supplyAsync(() -> transactionJpaRepository.getSecurityAccountTransactionsByTenant(idTenant,
            TransactionType.REDUCE.getValue()));
    final CompletableFuture<List<Object[]>> cfDateTransactionCurrency = CompletableFuture
        .supplyAsync(() -> historyquoteJpaRepository.getHistoryquoteCurrenciesForBuyAndSellByIdTenantAndMainCurrency(
            tenant.getIdTenant(), tenant.getCurrency()));
    final CompletableFuture<List<Currencypair>> currencypairsFuture = CompletableFuture.supplyAsync(
        () -> currencypairJpaRepository.getAllCurrencypairsByTenantInPortfolioAndAccounts(tenant.getIdTenant()));

    TransactionCostGrandSummary transactionCostGrandSummary = createTransactionCost(tenant,
        cfTransactionJpaRepository.join(), cfDateTransactionCurrency.join(), currencypairsFuture.join());

    return transactionCostGrandSummary;
  }

  private TransactionCostGrandSummary createTransactionCost(Tenant tenant, List<Transaction> transactions,
      List<Object[]> dateTransactionCurrency, List<Currencypair> currencypairs) {

    TransactionCostGrandSummary transactionCostGrandSummary = new TransactionCostGrandSummary(tenant.getCurrency(),
        globalparametersJpaRepository.getCurrencyPrecision());
    DateTransactionCurrencypairMap dateTransactionCurrencyMap = new DateTransactionCurrencypairMap(tenant.getCurrency(),
        null, dateTransactionCurrency, currencypairs, true, false);
    transactions.stream()
        .filter(transaction -> transaction.getTransactionCost() != null && transaction.getTransactionCost() != 0.0)
        .forEach(transaction -> {

          Assetclass assetclass = transaction.getSecurity().getAssetClass();
          if (!(assetclass.getCategoryType() == AssetclassType.MONEY_MARKET
              && assetclass.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT)) {
            this.calcCostAndTax(tenant.getCurrency(), transaction, transactionCostGrandSummary,
                dateTransactionCurrencyMap);
          }
        });
    transactionCostGrandSummary.caclulateGrandSummary();
    transactionCostGrandSummary.connectSecurityaccountToTransactionCostGroupSummary(tenant);

    return transactionCostGrandSummary;
  }

  /**
   *
   *
   * @param mainCurrency
   * @param transaction
   * @param transactionCostGrandSummary
   * @param dateTransactionCurrencyMap
   */
  private void calcCostAndTax(String mainCurrency, Transaction transaction,
      TransactionCostGrandSummary transactionCostGrandSummary,
      DateTransactionCurrencypairMap dateTransactionCurrencyMap) {

    TransactionCostPosition transactionCostPosition = new TransactionCostPosition(transaction,
        globalparametersJpaRepository.getPrecisionForCurrency(mainCurrency));
    transactionCostPosition.basePriceForTransactionCostMC = transaction.calcCostTaxMaybeBasePrice(mainCurrency,
        transactionCostPosition, dateTransactionCurrencyMap, true);

    TransactionCostGroupSummary transactionCostGroupSummary = transactionCostGrandSummary
        .getOrCreateGroup(transaction.getIdSecurityaccount());
    transactionCostGroupSummary.add(transactionCostPosition);
  }

}
