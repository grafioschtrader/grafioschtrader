package grafioschtrader.reports;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Component;

import grafiosch.common.DateHelper;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.types.AssetclassType;

/**
 * Comprehensive portfolio report that groups security positions by asset class type and includes cash account holdings
 * as pseudo-securities for complete portfolio allocation analysis. Provides a unified view of both invested assets and
 * liquid cash positions across all currencies, enabling accurate portfolio composition and allocation assessment.
 * 
 * <p>
 * This specialized report extends the base grouping functionality to address the critical requirement of including cash
 * holdings in portfolio analysis.
 * 
 * <h3>Cash Account Handling:</h3>
 * <p>
 * The report implements sophisticated cash account integration:
 * </p>
 * <ul>
 * <li><strong>Pseudo-Security Creation:</strong> Transforms cash accounts into security-like objects</li>
 * <li><strong>Currency Classification:</strong> Separates main currency from foreign currency cash</li>
 * <li><strong>Balance Calculation:</strong> Accurate cash balances based on transaction history</li>
 * <li><strong>Asset Class Assignment:</strong> Appropriate categorization for reporting consistency</li>
 * <li><strong>Negative ID Convention:</strong> Uses negative security IDs to distinguish cash from securities</li>
 * </ul>
 * 
 * <h3>Asset Class Categories:</h3>
 * <p>
 * Cash holdings are automatically classified into appropriate asset class types:
 * </p>
 * <ul>
 * <li><strong>CURRENCY_CASH:</strong> Cash in the portfolio's main reporting currency</li>
 * <li><strong>CURRENCY_FOREIGN:</strong> Cash in foreign currencies requiring conversion</li>
 * <li><strong>Traditional Asset Classes:</strong> Securities grouped by their natural classifications</li>
 * </ul>
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

  /**
   * Transforms cash accounts into pseudo-security representations for inclusion in asset class-based portfolio
   * analysis. This sophisticated process creates Security-like objects for each cash account, enabling unified
   * treatment of both invested assets and liquid cash within the portfolio reporting framework.
   * 
   * <p>
   * The transformation process implements several critical features:
   * </p>
   * 
   * <h4>Asset Class Assignment Strategy:</h4>
   * <ul>
   * <li><strong>Main Currency Cash:</strong> Assigned to CURRENCY_CASH asset class for domestic currency liquidity
   * tracking</li>
   * <li><strong>Foreign Currency Cash:</strong> Assigned to CURRENCY_FOREIGN asset class for international exposure and
   * currency risk assessment</li>
   * </ul>
   * 
   * <h4>Pseudo-Security Creation:</h4>
   * <p>
   * Each cash account is transformed into a Security object with:
   * </p>
   * <ul>
   * <li><strong>Negative ID Convention:</strong> Uses negative cash account ID to distinguish from real securities and
   * prevent ID conflicts</li>
   * <li><strong>Descriptive Naming:</strong> Preserves cash account name for clear identification in reports and
   * drill-down analysis</li>
   * </ul>
   * 
   * <h4>Balance Calculation:</h4>
   * <p>
   * Implements accurate balance determination using transaction-based calculation rather than cached balances, ensuring
   * temporal consistency with the report's date parameters. The calculation includes all transactions up to and
   * including the report date, providing accurate point-in-time cash position representation.
   * </p>
   * 
   * <h4>Integration with Portfolio Structure:</h4>
   * <p>
   * Processes all portfolios within the tenant's structure, ensuring comprehensive coverage of cash positions across
   * different investment accounts and strategies. Each cash account is processed individually to maintain granular
   * visibility while enabling aggregation at the asset class level.
   * </p>
   * 
   * <p>
   * The resulting pseudo-securities integrate seamlessly with the base reporting infrastructure, participating in
   * currency normalization, precision handling, and hierarchical aggregation alongside traditional securities.
   * </p>
   *
   * @param tenant                      the tenant whose portfolios contain cash accounts to be processed
   * @param securityPositionSummaryList the list to be augmented with cash position summaries
   * @param dateCurrencyMap             currency context and date parameters for balance calculation
   */
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
            security, globalparametersService.getCurrencyPrecision());
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
