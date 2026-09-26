package grafioschtrader.reportviews.securityaccount;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafioschtrader.algo.RebalancingPlan.ClassAdjustment;
import grafioschtrader.common.DataBusinessHelper;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Portfolio-level grand summary aggregating all security positions with multi-currency normalization")
public class SecurityPositionGrandSummary {
  public List<ClassAdjustment> classAdjustments;

  /**
   * Figures of the rebalancing comparison that describe the book as a whole. They are deliberately separate numbers
   * rather than one total: net equity, the cash actually on the accounts and gross exposure answer different questions,
   * and for a short or margin book they are not even close to each other.
   */
  @Schema(description = "Net equity: signed position values, cash and liabilities")
  public Double grandNetEquityMC;

  @Schema(description = "Cash actually held across all cash accounts, shown separately from equity")
  public Double grandActualCashMC;

  @Schema(description = "Sum of absolute position exposures, before offsetting long against short")
  public Double grandGrossExposureMC;

  @Schema(description = "Maximum investment budget: net equity times the AlgoTop ceiling")
  public Double grandInvestmentBudgetMC;

  @Schema(description = "Budget of tactical buckets that a rebalancing may not spend")
  public Double grandUnusedTacticalBudgetMC;

  @Schema(description = "Class trigger tolerance in percentage points of the target investment budget")
  public Double toleranceThreshold;

  @Schema(description = "Mismatch of normalized target and actual gross security exposures, excluding cash: 0 to 100 percent; null without positive targets")
  public Double overallAllocationMismatchPercentage;

  @Schema(description = """
      Gross exposure is above the permitted ceiling, or net equity is not positive. Exposure increasing
      recommendations are blocked while this holds; reductions remain available.""")
  public boolean exposureBreach;

  @Schema(description = "Closing day the comparison was calculated from")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate valuationDate;

  @Schema(description = """
      The valuation day is a periodic checkpoint, so every allocation beyond the tolerance is traded; between two
      checkpoints the recommendations are only reported.""")
  public Boolean periodicDue;

  @Schema(description = """
      Last periodic checkpoint the interval counts from. Only the hierarchy assigned to monitoring remembers one; null
      makes every day a checkpoint.""")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate lastCheckpointDate;

  @Schema(description = "First valuation day on which the next periodic checkpoint is due; null without a last checkpoint")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate nextCheckpointDate;

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
  public double grandGainLossCurrencyMC = 0.0;

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
    grandGainLossCurrencyMC += securityPositionGroupSummary.groupGainLossCurrencyMC;
  }

  public void roundGrandTotals() {
    grandAccountValueSecurityMC = DataBusinessHelper.round(grandAccountValueSecurityMC);
    grandGainLossSecurityMC = DataBusinessHelper.round(grandGainLossSecurityMC);
    grandGainLossCurrencyMC = DataBusinessHelper.round(grandGainLossCurrencyMC);
    grandTaxCostMC = DataBusinessHelper.round(grandTaxCostMC);
    grandSecurityRiskMC = DataBusinessHelper.roundStandard(grandSecurityRiskMC);
  }

  public Double getToleranceThreshold() {
    return toleranceThreshold == null ? null : DataBusinessHelper.roundPercentage(toleranceThreshold);
  }

  public Double getOverallAllocationMismatchPercentage() {
    return overallAllocationMismatchPercentage == null ? null
        : DataBusinessHelper.roundPercentage(overallAllocationMismatchPercentage);
  }

  /** Round report diagnostics on output, keeping the original plan available at calculation precision. */
  public List<ClassAdjustment> getClassAdjustments() {
    return classAdjustments == null ? null
        : classAdjustments.stream()
            .map(adjustment -> new ClassAdjustment(adjustment.idNode(),
                DataBusinessHelper.roundPercentage(adjustment.parentDeviation()),
                DataBusinessHelper.roundPercentage(adjustment.securityDeviationPercentage()),
                adjustment.maxTradedSecuritiesPerAssetclass(), adjustment.requestedAdjustment(),
                adjustment.plannedAdjustment(), adjustment.residual(), adjustment.limitingReason()))
            .toList();
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

  public double getGrandGainLossCurrencyMC() {
    return DataHelper.round(grandGainLossCurrencyMC, precision);
  }

}
