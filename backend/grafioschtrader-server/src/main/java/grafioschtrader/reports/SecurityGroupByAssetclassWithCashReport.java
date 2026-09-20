package grafioschtrader.reports;

import java.util.List;

import org.springframework.stereotype.Component;

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

}
