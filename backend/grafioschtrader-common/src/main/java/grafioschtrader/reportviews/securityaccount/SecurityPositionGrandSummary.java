package grafioschtrader.reportviews.securityaccount;

import java.util.ArrayList;
import java.util.List;

import grafiosch.common.DataHelper;
import grafioschtrader.common.DataBusinessHelper;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Portfolio-level grand summary aggregating all security positions with multi-currency normalization")
public class SecurityPositionGrandSummary {

  @Schema(description = "Main reporting currency for all normalized monetary values")
  public String currency;

  @Schema(description = "Total current market value of all security positions in main currency")
  public double grandAccountValueSecurityMC = 0.0;

  @Schema(description = "Total risk exposure aggregated across all security positions")
  public double grandSecurityRiskMC;

  @Schema(description = "Total gains/losses (realized and unrealized) across all security positions")
  public double grandGainLossSecurityMC = 0.0;

  @Schema(description = "Aggregated tax costs and implications across all positions")
  public double grandTaxCostMC = 0.0;

  @Schema(description = "Total currency exchange gains/losses from foreign currency exposure")
  public double grandCurrencyGainLossMC = 0.0;

  /**
   * Decimal precision for monetary value display based on the main currency's standard precision (e.g., 2 for USD/EUR,
   * 0 for JPY). Used by getter methods to provide appropriately rounded values for user interfaces and reports while
   * maintaining calculation accuracy in the underlying fields.
   */
  protected int precision;

  @Schema(description = "List of group summaries that comprise the grand total")
  public List<SecurityPositionGroupSummary> securityPositionGroupSummaryList = new ArrayList<>();

  public SecurityPositionGrandSummary(String currency, Integer precision) {
    this.currency = currency;
    this.precision = precision;
  }

  /**
   * Add the group total to the grand total, it should be called for each group.
   *
   * @param securityPositionGroupSummary the group summary to aggregate into grand totals
   */
  public void calcGrandTotal(SecurityPositionGroupSummary securityPositionGroupSummary) {
    securityPositionGroupSummaryList.add(securityPositionGroupSummary);
    grandAccountValueSecurityMC += securityPositionGroupSummary.groupAccountValueSecurityMC;
    grandGainLossSecurityMC += securityPositionGroupSummary.groupGainLossSecurityMC;
    grandSecurityRiskMC += securityPositionGroupSummary.groupSecurityRiskMC;
    grandCurrencyGainLossMC += securityPositionGroupSummary.groupCurrencyGainLossMC;
  }

  public void roundGrandTotals() {
    grandAccountValueSecurityMC = DataBusinessHelper.round(grandAccountValueSecurityMC);
    grandGainLossSecurityMC = DataBusinessHelper.round(grandGainLossSecurityMC);
    grandTaxCostMC = DataBusinessHelper.round(grandTaxCostMC);
    grandSecurityRiskMC = DataBusinessHelper.roundStandard(grandSecurityRiskMC);
  }

  public double getGrandAccountValueSecurityMC() {
    return DataHelper.round(grandAccountValueSecurityMC, precision);
  }

  public double getGrandSecurityRiskMC() {
    return DataHelper.round(grandSecurityRiskMC, precision);
  }

  public double getGrandGainLossSecurityMC() {
    return DataHelper.round(grandGainLossSecurityMC, precision);
  }

  public double getGrandTaxCostMC() {
    return DataHelper.round(grandTaxCostMC, precision);
  }

  public double getGrandCurrencyGainLossMC() {
    return DataHelper.round(grandCurrencyGainLossMC, precision);
  }

}
