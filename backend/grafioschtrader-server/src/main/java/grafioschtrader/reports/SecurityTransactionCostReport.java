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
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

/**
 * Specialized reporting service that analyzes and summarizes transaction costs across all security trading activities
 * within a tenant's portfolios. Provides comprehensive cost analysis including brokerage fees, taxes, and other
 * trading-related expenses with multi-currency support and intelligent filtering.
 * 
 * <p>
 * This report generator focuses specifically on the cost analysis aspect of trading activities, helping users
 * understand the impact of transaction costs on their investment performance. It processes all security transactions
 * that incur costs, excluding money market direct investments that typically don't have traditional brokerage fees.
 * </p>
 * 
 * <h3>Key Features:</h3>
 * <ul>
 * <li>Comprehensive transaction cost analysis across all portfolios</li>
 * <li>Multi-currency cost normalization to tenant's main currency</li>
 * <li>Intelligent filtering to exclude non-relevant transactions</li>
 * <li>Grouping by security account for detailed cost breakdown</li>
 * <li>Asynchronous data loading for optimal performance</li>
 * <li>Historical exchange rate integration for accurate cost conversion</li>
 * </ul>
 * 
 * 
 * <p>
 * The service automatically handles scenarios including foreign currency trading costs, historical exchange rate
 * conversions, and proper categorization of different cost types for comprehensive analysis and reporting.
 * </p>
 */
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
  private GlobalparametersService globalparametersService;

  /**
   * Generates a comprehensive transaction cost summary for all security trading activities within a tenant's
   * portfolios. Asynchronously loads transaction data, historical exchange rates, and currency pairs to provide
   * accurate cost analysis normalized to the tenant's main currency.
   * 
   * <p>
   * This method performs parallel data loading to optimize performance while ensuring all necessary information is
   * available for accurate cost calculations. It filters out transactions without costs and excludes money market
   * direct investments that don't typically incur traditional trading fees.
   * </p>
   * 
   * <p>
   * The resulting summary provides detailed cost breakdowns grouped by security account, enabling users to identify
   * high-cost trading patterns and optimize their trading strategies for better cost efficiency.
   * </p>
   * 
   * @param idTenant the unique identifier of the tenant for which to generate the cost report
   * @return comprehensive transaction cost summary with detailed breakdowns by security account and cost type, all
   *         values normalized to the tenant's main currency
   */
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

  /**
   * Processes transaction cost data to create a comprehensive cost summary with proper filtering, currency conversion,
   * and grouping. Excludes money market direct investments that don't have traditional transaction costs and focuses on
   * meaningful cost analysis.
   * 
   * <p>
   * This method applies intelligent filtering to exclude transactions that don't represent traditional trading costs,
   * such as money market direct investments. It then processes each qualifying transaction to calculate costs in the
   * main currency and organize them by security account for detailed analysis.
   * </p>
   * 
   * <p>
   * The currency conversion process uses historical exchange rates to ensure accurate cost representation regardless of
   * when the transactions occurred or what currencies were involved in the original trades.
   * </p>
   * 
   * @param tenant                  the tenant entity containing currency and configuration information
   * @param transactions            list of all security transactions that potentially have costs
   * @param dateTransactionCurrency historical exchange rate data for currency conversions
   * @param currencypairs           available currency pairs for the tenant's trading activities
   * @return completed transaction cost grand summary with calculated totals and groupings
   */
  private TransactionCostGrandSummary createTransactionCost(Tenant tenant, List<Transaction> transactions,
      List<Object[]> dateTransactionCurrency, List<Currencypair> currencypairs) {

    TransactionCostGrandSummary transactionCostGrandSummary = new TransactionCostGrandSummary(tenant.getCurrency(),
        globalparametersService.getCurrencyPrecision());
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
   * Calculates the cost and tax impact of a single transaction, converting all amounts to the main currency and
   * organizing the results by security account. Creates detailed cost position records that capture both the absolute
   * costs and their basis price impact.
   * 
   * <p>
   * This method handles the calculation of transaction costs including proper currency conversion using historical
   * exchange rates. It creates a detailed cost position that tracks not only the absolute cost amounts but also
   * calculates the basis price impact, which is crucial for understanding how costs affect the effective purchase or
   * sale price of securities.
   * </p>
   * 
   * <p>
   * The calculated costs are automatically organized by security account, enabling detailed analysis of cost patterns
   * and identification of high-cost trading relationships that may warrant optimization or renegotiation.
   * </p>
   * 
   * @param mainCurrency                the tenant's main currency for cost normalization
   * @param transaction                 the individual transaction to analyze for costs and taxes
   * @param transactionCostGrandSummary the grand summary to which this transaction's costs will be added
   * @param dateTransactionCurrencyMap  currency exchange rate context for accurate historical conversions
   */
  private void calcCostAndTax(String mainCurrency, Transaction transaction,
      TransactionCostGrandSummary transactionCostGrandSummary,
      DateTransactionCurrencypairMap dateTransactionCurrencyMap) {

    TransactionCostPosition transactionCostPosition = new TransactionCostPosition(transaction,
        globalparametersService.getPrecisionForCurrency(mainCurrency));
    transactionCostPosition.basePriceForTransactionCostMC = transaction.calcCostTaxMaybeBasePrice(mainCurrency,
        transactionCostPosition, dateTransactionCurrencyMap, true);

    TransactionCostGroupSummary transactionCostGroupSummary = transactionCostGrandSummary
        .getOrCreateGroup(transaction.getIdSecurityaccount());
    transactionCostGroupSummary.add(transactionCostPosition);
  }

}
