package grafioschtrader.reportviews.transactioncost;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reportviews.SecurityCostGrand;
import grafioschtrader.reportviews.SecurityCostGroup;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Comprehensive grand summary of all transaction costs across a tenant's portfolios, organized by security account.
 * Provides tenant-wide cost analysis with detailed breakdowns by broker/account and consolidated totals for cost
 * optimization insights.
 * 
 * <p>
 * This grand summary serves as the top-level aggregation for transaction cost analysis, combining costs from all
 * security accounts across all portfolios to provide a complete view of trading cost efficiency. It enables
 * identification of high-cost brokers, cost trends, and optimization opportunities at the enterprise level.
 * </p>
 */
@Schema(description = """
    Comprehensive tenant-wide summary of all transaction costs organized by security account, providing cost
    analysis and broker comparison capabilities""")
public class TransactionCostGrandSummary extends SecurityCostGrand<Integer, TransactionCostGroupSummary> {

  public TransactionCostGrandSummary(String currency, Map<String, Integer> currencyPrecisionMap) {
    super(currency, new HashMap<>(), currencyPrecisionMap);
  }

  @Override
  protected TransactionCostGroupSummary createInstance(Integer idSecurityaccount) {
    return new TransactionCostGroupSummary(idSecurityaccount,
        currencyPrecisionMap.getOrDefault(mainCurrency, GlobalConstants.FID_STANDARD_FRACTION_DIGITS));
  }

  public Collection<TransactionCostGroupSummary> getTransactionCostGroupSummaries() {
    return this.groupMap.values();
  }

  /**
   * Associates security account entities with their corresponding cost summaries by traversing the tenant's portfolio
   * structure. This enrichment process adds broker details, account configurations, and other contextual information
   * necessary for comprehensive cost analysis and reporting.
   * 
   * <p>
   * This method links the cost data with the actual security account entities, enabling detailed analysis of broker
   * relationships, account settings, and trading configurations that may impact cost efficiency.
   * </p>
   * 
   * @param tenant the tenant entity containing the portfolio and security account structure
   */
  public void connectSecurityaccountToTransactionCostGroupSummary(Tenant tenant) {
    tenant.getPortfolioList().forEach(portfolio -> {
      portfolio.getSecurityaccountList().forEach(securityaccount -> {
        TransactionCostGroupSummary transactionCostGroupSummary = groupMap.get(securityaccount.getId());
        if (transactionCostGroupSummary != null) {
          transactionCostGroupSummary.securityaccount = securityaccount;
        }
      });
    });
  }

  @Override
  public SecurityCostGroup getSecurityCostGroup(TransactionCostGroupSummary groupSummary) {
    return groupSummary;
  }

}
