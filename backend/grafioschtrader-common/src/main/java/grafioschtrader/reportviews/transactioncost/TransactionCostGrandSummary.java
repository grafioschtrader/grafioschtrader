package grafioschtrader.reportviews.transactioncost;

import java.util.Collection;
import java.util.HashMap;

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

  public TransactionCostGrandSummary(String currency) {
    super(currency, new HashMap<>());
  }

  @Override
  protected TransactionCostGroupSummary createInstance(Integer idSecurityaccount) {
    return new TransactionCostGroupSummary(idSecurityaccount);
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
