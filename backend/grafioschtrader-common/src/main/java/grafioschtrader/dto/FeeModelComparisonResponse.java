package grafioschtrader.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response wrapper for fee model comparison: summary statistics plus detail rows.
 */
@Schema(description = """
    Comparison of actual transaction costs vs EvalEx fee model estimates for a security account.
    Contains summary statistics and per-transaction detail rows.""")
public class FeeModelComparisonResponse {

  @Schema(description = "Name of the TradingPlatformPlan used for estimation")
  private String planName;

  @Schema(description = "Total number of BUY/SELL transactions in the account")
  private int totalTransactions;

  @Schema(description = "Number of transactions skipped (null or zero cost)")
  private int skippedCount;

  @Schema(description = "Number of transactions where estimation failed")
  private int errorCount;

  @Schema(description = "Number of transactions successfully compared")
  private int comparedCount;

  @Schema(description = "Mean of actual transaction costs")
  private Double meanActualCost;

  @Schema(description = "Mean of estimated transaction costs")
  private Double meanEstimatedCost;

  @Schema(description = "Mean absolute error (|estimated - actual|)")
  private Double meanAbsoluteError;

  @Schema(description = "Mean relative error in percent")
  private Double meanRelativeError;

  @Schema(description = "Root mean squared error")
  private Double rmse;

  @Schema(description = "Per-transaction comparison detail rows")
  private List<FeeModelComparisonDetail> details;

  public String getPlanName() {
    return planName;
  }

  public void setPlanName(String planName) {
    this.planName = planName;
  }

  public int getTotalTransactions() {
    return totalTransactions;
  }

  public void setTotalTransactions(int totalTransactions) {
    this.totalTransactions = totalTransactions;
  }

  public int getSkippedCount() {
    return skippedCount;
  }

  public void setSkippedCount(int skippedCount) {
    this.skippedCount = skippedCount;
  }

  public int getErrorCount() {
    return errorCount;
  }

  public void setErrorCount(int errorCount) {
    this.errorCount = errorCount;
  }

  public int getComparedCount() {
    return comparedCount;
  }

  public void setComparedCount(int comparedCount) {
    this.comparedCount = comparedCount;
  }

  public Double getMeanActualCost() {
    return meanActualCost;
  }

  public void setMeanActualCost(Double meanActualCost) {
    this.meanActualCost = meanActualCost;
  }

  public Double getMeanEstimatedCost() {
    return meanEstimatedCost;
  }

  public void setMeanEstimatedCost(Double meanEstimatedCost) {
    this.meanEstimatedCost = meanEstimatedCost;
  }

  public Double getMeanAbsoluteError() {
    return meanAbsoluteError;
  }

  public void setMeanAbsoluteError(Double meanAbsoluteError) {
    this.meanAbsoluteError = meanAbsoluteError;
  }

  public Double getMeanRelativeError() {
    return meanRelativeError;
  }

  public void setMeanRelativeError(Double meanRelativeError) {
    this.meanRelativeError = meanRelativeError;
  }

  public Double getRmse() {
    return rmse;
  }

  public void setRmse(Double rmse) {
    this.rmse = rmse;
  }

  public List<FeeModelComparisonDetail> getDetails() {
    return details;
  }

  public void setDetails(List<FeeModelComparisonDetail> details) {
    this.details = details;
  }
}
