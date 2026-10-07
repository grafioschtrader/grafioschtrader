package grafioschtrader.reportviews.securityaccount;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafioschtrader.algo.RebalancingPlan.ClassAdjustment;
import grafioschtrader.common.DataBusinessHelper;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Portfolio-level grand summary aggregating all security positions with multi-currency normalization")
public class SecurityPositionGrandSummary {
  /** Conversion rates used by this valuation, retained for document rendering without a second quote query. */
  @com.fasterxml.jackson.annotation.JsonIgnore
  public java.util.Map<String, Double> exchangeRates = java.util.Map.of();

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

  @Schema(description = "AlgoTop ceiling in percentage points of net equity")
  public Double topTargetPercentage;

  @Schema(description = "Actual gross exposure in percentage points of net equity; null when net equity is zero")
  public Double topActualPercentage;

  @Schema(description = """
      Actual gross exposure minus the AlgoTop ceiling, in percentage points of net equity. Positive means above the
      ceiling; null when the actual share is unknown.""")
  public Double topDeviationPercentage;

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

  @Schema(description = """
      Sum of the known disposal costs of all estimated positions in main currency. Null unless the disposal cost
      estimate is switched on and at least one position was estimated.""")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Double grandDisposalCostMC;

  @Schema(description = "False when the disposal costs of at least one position are incomplete")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Boolean grandDisposalComplete;

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
    if (securityPositionGroupSummary.groupDisposalCostMC != null) {
      grandDisposalCostMC = (grandDisposalCostMC == null ? 0.0 : grandDisposalCostMC)
          + securityPositionGroupSummary.groupDisposalCostMC;
      grandDisposalComplete = (grandDisposalComplete == null || grandDisposalComplete)
          && securityPositionGroupSummary.groupDisposalComplete;
    }
  }

  /**
   * Sets the share of every position and every group in the report total, so that the shares of all positions add up
   * to 100. The denominator is the net total, which is why a position with a negative account value - a CFD or Forex
   * position at a loss, an overdrawn cash account - gets a negative share and the remaining positions together exceed
   * 100. When the total is not positive the shares carry no meaning (a negative denominator would invert every sign),
   * so they are all reset to null. Must be called again whenever the group structure changes after the grand total was
   * calculated.
   */
  public void calcShareOfTotalPercentages() {
    boolean positiveTotal = grandAccountValueSecurityMC > 0;
    for (SecurityPositionGroupSummary group : securityPositionGroupSummaryList) {
      group.groupShareOfTotalPercentage = positiveTotal ? shareOfTotal(group.groupAccountValueSecurityMC) : null;
      for (SecurityPositionSummary position : group.securityPositionSummaryList) {
        position.shareOfTotalPercentage = positiveTotal ? shareOfTotal(position.accountValueSecurityMC) : null;
      }
    }
  }

  private double shareOfTotal(double accountValueMC) {
    return accountValueMC * 100.0 / grandAccountValueSecurityMC;
  }

  @Schema(description = "Share of the report total in percentage points: 100, or null when the total is not positive")
  public Double getGrandShareOfTotalPercentage() {
    return grandAccountValueSecurityMC > 0 ? 100.0 : null;
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

  public Double getTopTargetPercentage() {
    return topTargetPercentage == null ? null : DataBusinessHelper.roundPercentage(topTargetPercentage);
  }

  public Double getTopActualPercentage() {
    return topActualPercentage == null ? null : DataBusinessHelper.roundPercentage(topActualPercentage);
  }

  public Double getTopDeviationPercentage() {
    return topDeviationPercentage == null ? null : DataBusinessHelper.roundPercentage(topDeviationPercentage);
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

  public Double getGrandDisposalCostMC() {
    return grandDisposalCostMC == null ? null : DataHelper.round(grandDisposalCostMC, precision);
  }

  @Schema(description = "Total account value in main currency less the known disposal costs")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Double getGrandValueAfterDisposalMC() {
    return grandDisposalCostMC == null ? null
        : DataHelper.round(grandAccountValueSecurityMC - grandDisposalCostMC, precision);
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
