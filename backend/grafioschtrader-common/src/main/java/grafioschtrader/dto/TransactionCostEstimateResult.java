package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of an EvalEx-based transaction cost estimation.")
public class TransactionCostEstimateResult {

  @Schema(description = "Estimated transaction cost, null if no rule matched or an error occurred")
  private Double estimatedCost;

  @Schema(description = "Name of the fee rule that matched, null if no match")
  private String matchedRuleName;

  @Schema(description = "Error message if evaluation failed, null on success")
  private String error;

  public TransactionCostEstimateResult() {
  }

  public static TransactionCostEstimateResult success(double cost, String ruleName) {
    TransactionCostEstimateResult r = new TransactionCostEstimateResult();
    r.estimatedCost = cost;
    r.matchedRuleName = ruleName;
    return r;
  }

  public static TransactionCostEstimateResult error(String errorMessage) {
    TransactionCostEstimateResult r = new TransactionCostEstimateResult();
    r.error = errorMessage;
    return r;
  }

  public Double getEstimatedCost() {
    return estimatedCost;
  }

  public void setEstimatedCost(Double estimatedCost) {
    this.estimatedCost = estimatedCost;
  }

  public String getMatchedRuleName() {
    return matchedRuleName;
  }

  public void setMatchedRuleName(String matchedRuleName) {
    this.matchedRuleName = matchedRuleName;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }
}
