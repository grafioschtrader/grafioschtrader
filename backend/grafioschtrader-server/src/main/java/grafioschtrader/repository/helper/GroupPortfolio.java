package grafioschtrader.repository.helper;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.reportviews.account.AccountPositionGroupSummary;

/**
 * Create a group per Portfolio.
 *
 * @author Hugo Graf
 *
 */
public class GroupPortfolio extends AccountGroupMap<Portfolio> {

  @Override
  public AccountPositionGroupSummary getAccountPositionGroupSummary(final Cashaccount cashaccount) {
    return mapAccountGrandSummary.computeIfAbsent(cashaccount.getPortfolio(),
        portfolio -> new AccountPositionGroupSummary(portfolio.getName(), portfolio.getCurrency()));
  }

}
