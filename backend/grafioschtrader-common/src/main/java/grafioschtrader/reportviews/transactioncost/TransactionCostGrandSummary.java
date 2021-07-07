package grafioschtrader.reportviews.transactioncost;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reportviews.SecurityCostGrand;
import grafioschtrader.reportviews.SecurityCostGroup;

/**
 * Grand total of transaction cost report
 *
 * @author Hugo Graf
 *
 */
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
