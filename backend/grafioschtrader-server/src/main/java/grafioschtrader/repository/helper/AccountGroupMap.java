package grafioschtrader.repository.helper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.account.AccountPositionGrandSummary;
import grafioschtrader.reportviews.account.AccountPositionGroupSummary;
import grafioschtrader.reportviews.account.CashaccountPositionSummary;

public abstract class AccountGroupMap<K> {

  Map<K, AccountPositionGroupSummary> mapAccountGrandSummary = new HashMap<>();

  public abstract AccountPositionGroupSummary getAccountPositionGroupSummary(Cashaccount cashaccount);

  public List<AccountPositionGroupSummary> getGroupSummaryList() {
    return new ArrayList<>(mapAccountGrandSummary.values());
  }

  public AccountPositionGrandSummary getGrandGroupSummary(
      final DateTransactionCurrencypairMap dateTransactionCurrencypairMap, int precisionMC) {
    final AccountPositionGrandSummary accountPositionGrandSummary = new AccountPositionGrandSummary(
        dateTransactionCurrencypairMap.getMainCurrency(), precisionMC);
    accountPositionGrandSummary.accountPositionGroupSummaryList = getGroupSummaryList();
    accountPositionGrandSummary.calcTotals(dateTransactionCurrencypairMap);
    return accountPositionGrandSummary;
  }

  public List<CashaccountPositionSummary> getAllForeignCurrency() {
    return mapAccountGrandSummary.values().stream().map(value -> value.accountPositionSummaryList)
        .flatMap(values -> values.stream())
        .filter(accountPositionSummary -> accountPositionSummary.securitycurrency != null).collect(Collectors.toList());

  }

}
