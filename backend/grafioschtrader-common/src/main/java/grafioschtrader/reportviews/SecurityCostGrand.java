package grafioschtrader.reportviews;

import java.util.Map;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Abstract base class for creating grand totals and comprehensive summaries of security-related costs across multiple
 * groupings. Provides the foundation for enterprise-level cost analysis by aggregating costs from various group
 * summaries and calculating meaningful statistics for cost optimization and trend analysis.
 * 
 * <p>
 * This generic base class establishes the pattern for creating comprehensive cost reports that span multiple dimensions
 * (accounts, currencies, time periods, etc.) while maintaining proper currency precision and providing standardized
 * statistical calculations for cost analysis.
 * </p>
 * 
 * <h3>Core Capabilities:</h3>
 * <ul>
 * <li>Aggregation of costs across multiple group summaries</li>
 * <li>Grand total calculations for transaction and tax costs</li>
 * <li>Enterprise-wide average cost calculations</li>
 * <li>Multi-currency normalization with proper precision handling</li>
 * <li>Flexible grouping strategy through generic type parameters</li>
 * </ul>
 */
public abstract class SecurityCostGrand<S, T> extends MapGroup<S, T> {
  @Schema(description = "Main currency used for cost normalization and reporting (ISO 4217)")
  public String mainCurrency;

  @Schema(description = "Total number of transactions across all groups (including those with zero costs)")
  public double grandCountTransaction;

  @Schema(description = "Total number of paid transactions that incurred costs across all groups")
  public double grandCountPaidTransaction;

  @Schema(description = "Grand total of all tax costs (trading taxes, regulatory fees) across all groups")
  public double grandTotalTaxCostMC;

  @Schema(description = "Grand total of all transaction costs (brokerage fees, commissions) across all groups")
  public double grandTotalTransactionCostMC;

  @Schema(description = "Enterprise-wide average transaction cost per cost-bearing transaction")
  public double grandTotalAverageTransactionCostMC;

  /**
   * Currency precision configuration map defining decimal places for each currency code. Ensures accurate monetary
   * calculations and proper rounding according to each currency's standard precision requirements.
   */
  protected Map<String, Integer> currencyPrecisionMap;

  /**
   * Number of decimal places for monetary precision in the main currency. Used for consistent rounding of all grand
   * total values in the reporting currency.
   */
  protected int precisionMC;

  /**
   * Abstract method that extracts the security cost group from a specific group summary. Enables polymorphic access to
   * cost group functionality regardless of the specific group summary implementation, supporting flexible grouping
   * strategies.
   * 
   * @param groupSummary the group summary from which to extract the cost group
   * @return the security cost group for aggregation and calculation operations
   */
  public abstract SecurityCostGroup getSecurityCostGroup(T groupSummary);

  public SecurityCostGrand(String currency, Map<S, T> groupMap, Map<String, Integer> currencyPrecisionMap) {
    super(groupMap);
    this.mainCurrency = currency;
    this.currencyPrecisionMap = currencyPrecisionMap;
    this.precisionMC = currencyPrecisionMap.getOrDefault(mainCurrency, BaseConstants.FID_STANDARD_FRACTION_DIGITS);
  }

  public double getGrandTotalTaxCostMC() {
    return DataHelper.round(grandTotalTaxCostMC, precisionMC);
  }

  public double getGrandTotalTransactionCostMC() {
    return DataHelper.round(grandTotalTransactionCostMC, precisionMC);
  }

  public double getGrandTotalAverageTransactionCostMC() {
    return DataHelper.round(grandTotalAverageTransactionCostMC, precisionMC);
  }

  /**
   * Calculates comprehensive grand totals by aggregating costs from all group summaries.
   * This method coordinates the calculation process across all groups, ensuring each
   * group's totals are current before performing enterprise-level aggregation.
   */
  public void caclulateGrandSummary() {
    this.groupMap.forEach((idSecuritycurrency, groupSummary) -> {
      SecurityCostGroup securityCostGroup = getSecurityCostGroup(groupSummary);
      securityCostGroup.caclulateGroupSummary();
      grandTotalTaxCostMC += securityCostGroup.groupTotalTaxCostMC;
      grandTotalTransactionCostMC += securityCostGroup.groupTotalTransactionCostMC;
      grandCountPaidTransaction += securityCostGroup.groupCountPaidTransaction;
    });
    if (grandCountPaidTransaction > 0) {
      grandTotalAverageTransactionCostMC = grandTotalTransactionCostMC / grandCountPaidTransaction;
    }
  }

}
