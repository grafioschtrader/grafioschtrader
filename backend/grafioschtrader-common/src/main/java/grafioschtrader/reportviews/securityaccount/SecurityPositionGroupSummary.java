package grafioschtrader.reportviews.securityaccount;

import java.util.ArrayList;
import java.util.List;

import grafiosch.common.DataHelper;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Base class for aggregating security positions into logical groups by classification criteria")
public abstract class SecurityPositionGroupSummary {

  @Schema(description = "Total current market value of all securities in the group in main currency")
  public double groupAccountValueSecurityMC;

  @Schema(description = "Total gains/losses for all securities in the group in main currency")
  public double groupGainLossSecurityMC = 0.0;

  @Schema(description = "Total currency exchange gains/losses for the group in main currency")
  public double groupCurrencyGainLossMC = 0.0;

  @Schema(description = "Total value of short positions in the group adjusted for leverage")
  public double groupValueSecurityShort;

  @Schema(description = "Total risk exposure for all securities in the group in main currency")
  public double groupSecurityRiskMC;

  @Schema(description = "List of individual security position summaries within this group")
  public List<SecurityPositionSummary> securityPositionSummaryList = new ArrayList<>();

  /**
   * Decimal precision for monetary values in the original security currency. Used by getter methods to provide
   * appropriately rounded values for display while maintaining calculation accuracy in the underlying aggregation
   * fields.
   */
  protected int precision;

  /**
   * Decimal precision for monetary values converted to the main currency. Ensures proper rounding for main currency
   * conversions, which may have different precision requirements than the original security currencies.
   */
  private int precisionMC;

  public SecurityPositionGroupSummary(int precision) {
    this.precision = precision;
  }

  /**
   * Adds a security position to this group and aggregates its financial metrics into the group totals. This method
   * performs the core aggregation logic, updating all relevant group-level financial metrics with the values from the
   * individual position.
   * 
   * <p>
   * Leverage factor considerations are critical for accurate risk assessment, particularly for derivative instruments,
   * margin trading, and leveraged ETFs where the actual risk exposure may be significantly different from the nominal
   * position value.
   * </p>
   * 
   * @param securityPositionSummary the individual security position to add to this group
   */
  public void addToGroupSummaryAndCalcGroupTotals(SecurityPositionSummary securityPositionSummary) {
    precision = securityPositionSummary.precision;
    precisionMC = securityPositionSummary.precisionMC;
    securityPositionSummaryList.add(securityPositionSummary);
    groupGainLossSecurityMC += securityPositionSummary.gainLossSecurityMC;
    groupCurrencyGainLossMC += securityPositionSummary.currencyGainLossMC;
    groupAccountValueSecurityMC += securityPositionSummary.accountValueSecurityMC;

    groupValueSecurityShort += securityPositionSummary.valueSecurity
        * securityPositionSummary.securitycurrency.getLeverageFactor();
    securityPositionSummary.securityRiskMC = securityPositionSummary.valueSecurityMC
        * securityPositionSummary.securitycurrency.getLeverageFactor();

    groupSecurityRiskMC += securityPositionSummary.securityRiskMC;

  }

  public double getGroupAccountValueSecurityMC() {
    return DataHelper.round(groupAccountValueSecurityMC, precisionMC);
  }

  public double getGroupGainLossSecurityMC() {
    return DataHelper.round(groupGainLossSecurityMC, precisionMC);
  }

  public double getGroupValueSecurityShort() {
    return DataHelper.round(groupValueSecurityShort, precision);
  }

  public double getGroupSecurityRiskMC() {
    return DataHelper.round(groupSecurityRiskMC, precisionMC);
  }

  public double getGroupCurrencyGainLossMC() {
    return DataHelper.round(groupCurrencyGainLossMC, precisionMC);
  }

}
