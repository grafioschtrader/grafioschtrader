package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Request DTO for estimating transaction costs using the EvalEx-based fee model
    configured on a TradingPlatformPlan.""")
public class TransactionCostEstimateRequest {

  @Schema(description = "Tenant-owned securities account whose unsaved override is previewed")
  private Integer idSecurityaccount;

  public Integer getIdSecurityaccount() {
    return idSecurityaccount;
  }

  public void setIdSecurityaccount(Integer idSecurityaccount) {
    this.idSecurityaccount = idSecurityaccount;
  }

  @Schema(description = "ID of the TradingPlatformPlan whose fee model to evaluate")
  private Integer idTradingPlatformPlan;

  @Schema(description = "Total trade amount (price x units)")
  private Double tradeValue;

  @Schema(description = "Number of shares/units traded")
  private Double units;

  @Schema(description = "Investment instrument type ordinal (0=DIRECT, 1=ETF, 2=FUND, etc.)")
  private Integer specInvestInstrument;

  @Schema(description = "Asset class type ordinal (0=EQUITIES, 1=FIXED_INCOME, etc.)")
  private Integer categoryType;

  @Schema(description = "Market Identifier Code of the stock exchange (e.g., XSWX, XNYS)")
  private String mic;

  @Schema(description = "Trade currency ISO code (e.g., CHF, USD)")
  private String currency;

  @Schema(description = "Total portfolio/account value for tier determination")
  private Double fixedAssets;

  @Schema(description = "Trade direction: 0 = buy, 1 = sell")
  private Integer tradeDirection;

  @Schema(description = "Transaction date in YYYY-MM-DD format. Used to select the matching fee period. Defaults to today if null.")
  private String transactionDate;

  @Schema(description = """
      ISO code of the cash account the trade settles in. Lets a rule charge a currency conversion mark-up only when it
      differs from the instrument currency. Empty when unknown.""")
  private String settlementCurrency;

  @Schema(description = "Earlier buy/sell trades of the same security account in the calendar month of this trade")
  private Integer tradesInMonth;

  @Schema(description = "Earlier buy/sell trades of the same security account in the calendar quarter of this trade")
  private Integer tradesInQuarter;

  @Schema(description = "Earlier buy/sell trades of the same security account in the calendar year of this trade")
  private Integer tradesInYear;

  @Schema(description = """
      Earlier buy/sell trades of the same security in the same security account in the calendar month of this trade,
      for monthly free-order allowances per instrument.""")
  private Integer securityTradesInMonth;

  @Schema(description = "Optional inline YAML for direct evaluation, bypassing DB lookup.")
  private String yaml;

  public String getSettlementCurrency() {
    return settlementCurrency;
  }

  public void setSettlementCurrency(String settlementCurrency) {
    this.settlementCurrency = settlementCurrency;
  }

  public Integer getTradesInMonth() {
    return tradesInMonth;
  }

  public void setTradesInMonth(Integer tradesInMonth) {
    this.tradesInMonth = tradesInMonth;
  }

  public Integer getTradesInQuarter() {
    return tradesInQuarter;
  }

  public void setTradesInQuarter(Integer tradesInQuarter) {
    this.tradesInQuarter = tradesInQuarter;
  }

  public Integer getTradesInYear() {
    return tradesInYear;
  }

  public void setTradesInYear(Integer tradesInYear) {
    this.tradesInYear = tradesInYear;
  }

  public Integer getSecurityTradesInMonth() {
    return securityTradesInMonth;
  }

  public void setSecurityTradesInMonth(Integer securityTradesInMonth) {
    this.securityTradesInMonth = securityTradesInMonth;
  }

  public Integer getIdTradingPlatformPlan() {
    return idTradingPlatformPlan;
  }

  public void setIdTradingPlatformPlan(Integer idTradingPlatformPlan) {
    this.idTradingPlatformPlan = idTradingPlatformPlan;
  }

  public Double getTradeValue() {
    return tradeValue;
  }

  public void setTradeValue(Double tradeValue) {
    this.tradeValue = tradeValue;
  }

  public Double getUnits() {
    return units;
  }

  public void setUnits(Double units) {
    this.units = units;
  }

  public Integer getSpecInvestInstrument() {
    return specInvestInstrument;
  }

  public void setSpecInvestInstrument(Integer specInvestInstrument) {
    this.specInvestInstrument = specInvestInstrument;
  }

  public Integer getCategoryType() {
    return categoryType;
  }

  public void setCategoryType(Integer categoryType) {
    this.categoryType = categoryType;
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

  public Double getFixedAssets() {
    return fixedAssets;
  }

  public void setFixedAssets(Double fixedAssets) {
    this.fixedAssets = fixedAssets;
  }

  public Integer getTradeDirection() {
    return tradeDirection;
  }

  public void setTradeDirection(Integer tradeDirection) {
    this.tradeDirection = tradeDirection;
  }

  public String getTransactionDate() {
    return transactionDate;
  }

  public void setTransactionDate(String transactionDate) {
    this.transactionDate = transactionDate;
  }

  public String getYaml() {
    return yaml;
  }

  public void setYaml(String yaml) {
    this.yaml = yaml;
  }
}
