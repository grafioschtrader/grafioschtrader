package grafioschtrader.reports;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Component;

import grafioschtrader.common.DateHelper;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.types.AssetclassType;

/**
 * Report which is grouped by asset class type. It also includes every single
 * cash account.
 *
 * @author Hugo Graf
 *
 */
@Component
public class SecurityGroupByAssetclassWithCashReport extends SecurityGroupByBaseReport<AssetclassType> {

  public SecurityGroupByAssetclassWithCashReport() {
    super(SecurityGroupByBaseReport.ASSETCLASS_CATEGORY_FIELD_NAME);
  }

  @Override
  protected SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<AssetclassType>> createGroupsAndCalcGrandTotal(
      final Tenant tenant, List<SecurityPositionSummary> securityPositionSummaryList,
      DateTransactionCurrencypairMap dateCurrencyMap) throws Exception {
    this.addCashaccountAsASecurity(tenant, securityPositionSummaryList, dateCurrencyMap);

    return super.createGroupsAndCalcGrandTotal(tenant, securityPositionSummaryList, dateCurrencyMap);
  }

  private void addCashaccountAsASecurity(final Tenant tenant, List<SecurityPositionSummary> securityPositionSummaryList,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    Assetclass assetclassMainCurrency = new Assetclass();
    assetclassMainCurrency.setCategoryType(AssetclassType.CURRENCY_CASH);
    Assetclass assetclassForeignCurrency = new Assetclass();
    assetclassForeignCurrency.setCategoryType(AssetclassType.CURRENCY_FOREIGN);
    Date untilDatePlus = DateHelper.setTimeToZeroAndAddDay(dateCurrencyMap.getUntilDate(), 1);

    tenant.getPortfolioList().forEach(portfolio -> {
      portfolio.getCashaccountList().forEach(cashaccount -> {
        Security security = new Security();
        security.setIdSecuritycurrency(cashaccount.getId() * -1);
        security.setName(cashaccount.getName());
        security.setCurrency(cashaccount.getCurrency());
        SecurityPositionSummary securityPositionSummary = new SecurityPositionSummary(dateCurrencyMap.getMainCurrency(),
            security, globalparametersJpaRepository.getCurrencyPrecision());
        securityPositionSummaryList.add(securityPositionSummary);

        securityPositionSummary.valueSecurity = cashaccount.calculateBalanceOnTransactions(untilDatePlus);
        // securityPositionSummary.valueSecurity = cashaccount.getBalance();
        if (cashaccount.getCurrency().equals(dateCurrencyMap.getMainCurrency())) {
          security.setAssetClass(assetclassMainCurrency);
        } else {
          security.setAssetClass(assetclassForeignCurrency);
        }
      });
    });
  }

}
