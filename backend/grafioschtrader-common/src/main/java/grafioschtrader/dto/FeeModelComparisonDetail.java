package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single comparison row representing one BUY/SELL transaction with its actual cost
 * compared against the EvalEx fee model estimate.
 */
@Schema(description = """
    Detail row for fee model comparison: one transaction with actual vs estimated cost.""")
public class FeeModelComparisonDetail {

  @Schema(description = "Date of the transaction")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  private LocalDate transactionDate;

  @Schema(description = "BUY or SELL")
  private String transactionType;

  @Schema(description = "Name of the traded security")
  private String securityName;

  @Schema(description = "Asset class type enum name (e.g. EQUITIES, FIXED_INCOME)")
  private String categoryType;

  @Schema(description = "Special investment instrument enum name (e.g. DIRECT_INVESTMENT, ETF)")
  private String specInvestInstrument;

  @Schema(description = "Market Identifier Code of the stock exchange")
  private String mic;

  @Schema(description = "Trade currency ISO code")
  private String currency;

  @Schema(description = "Price per unit at transaction time")
  private double quotation;

  @Schema(description = "Number of units traded")
  private double units;

  @Schema(description = "Total trade value (quotation x units)")
  private double tradeValue;

  @Schema(description = "Actual recorded transaction cost")
  private double actualCost;

  @Schema(description = "Estimated cost from fee model, null if error")
  private Double estimatedCost;

  @Schema(description = "Relative error percentage, null if not computable")
  private Double relativeError;

  @Schema(description = "Name of the fee rule that matched")
  private String matchedRuleName;

  @Schema(description = "Error message if evaluation failed, null on success")
  private String error;

  public LocalDate getTransactionDate() {
    return transactionDate;
  }

  public void setTransactionDate(LocalDate transactionDate) {
    this.transactionDate = transactionDate;
  }

  public String getTransactionType() {
    return transactionType;
  }

  public void setTransactionType(String transactionType) {
    this.transactionType = transactionType;
  }

  public String getSecurityName() {
    return securityName;
  }

  public void setSecurityName(String securityName) {
    this.securityName = securityName;
  }

  public String getCategoryType() {
    return categoryType;
  }

  public void setCategoryType(String categoryType) {
    this.categoryType = categoryType;
  }

  public String getSpecInvestInstrument() {
    return specInvestInstrument;
  }

  public void setSpecInvestInstrument(String specInvestInstrument) {
    this.specInvestInstrument = specInvestInstrument;
  }

  public String getMic() {
    return mic;
  }

  public void setMic(String mic) {
    this.mic = mic;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public double getQuotation() {
    return quotation;
  }

  public void setQuotation(double quotation) {
    this.quotation = quotation;
  }

  public double getUnits() {
    return units;
  }

  public void setUnits(double units) {
    this.units = units;
  }

  public double getTradeValue() {
    return tradeValue;
  }

  public void setTradeValue(double tradeValue) {
    this.tradeValue = tradeValue;
  }

  public double getActualCost() {
    return actualCost;
  }

  public void setActualCost(double actualCost) {
    this.actualCost = actualCost;
  }

  public Double getEstimatedCost() {
    return estimatedCost;
  }

  public void setEstimatedCost(Double estimatedCost) {
    this.estimatedCost = estimatedCost;
  }

  public Double getRelativeError() {
    return relativeError;
  }

  public void setRelativeError(Double relativeError) {
    this.relativeError = relativeError;
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
