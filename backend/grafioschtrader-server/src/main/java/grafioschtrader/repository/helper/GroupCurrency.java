package grafioschtrader.repository.helper;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.reportviews.account.AccountPositionGroupSummary;

public class GroupCurrency extends AccountGroupMap<String> {

  @Override
  public AccountPositionGroupSummary getAccountPositionGroupSummary(final Cashaccount cashaccount) {
    return mapAccountGrandSummary.computeIfAbsent(cashaccount.getCurrency(),
        currency -> new AccountPositionGroupSummary(currency, currency));
  }

}