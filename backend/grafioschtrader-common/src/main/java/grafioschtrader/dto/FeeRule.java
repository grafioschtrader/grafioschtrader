package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    A single fee rule with an EvalEx boolean condition and a numeric expression for fee calculation.
    String variables: instrument (DIRECT_INVESTMENT, ETF, MUTUAL_FUND, ...), assetclass (EQUITIES,
    FIXED_INCOME, ...), mic, currency, settlementCurrency. Numeric variables: tradeValue, units, fixedAssets (the
    securities value of the security account), tradeDirection, and the trade counts tradesInMonth, tradesInQuarter,
    tradesInYear, securityTradesInMonth (earlier trades of the security account in the calendar period).
    portfolioTotal and tenantTotal are the total value (securities, cash balance and result of closed margin
    positions) of the portfolio of the security account, in the portfolio currency, and of all portfolios of the
    tenant, in the tenant currency, each at the close of the last day before the trade date. They come from the daily
    total value; when it is missing for that day the estimate stays incomplete instead of using 0.
    Legacy numeric aliases: specInvestInstrument, categoryType.
    In a custody value rule the variables are positionValue, accountValue, instrument, assetclass, isin, currency, mic.""")
public class FeeRule {

  @Schema(description = "Human-readable rule name (e.g., 'Swiss stocks - Premium tier')")
  private String name;

  @Schema(description = "EvalEx boolean expression. Use 'true' for a catch-all default rule.")
  private String condition;

  @Schema(description = "EvalEx numeric expression for fee calculation. Example: 'MAX(9.0, tradeValue * 0.001)'")
  private String expression;

  public FeeRule() {
  }

  public FeeRule(String name, String condition, String expression) {
    this.name = name;
    this.condition = condition;
    this.expression = expression;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCondition() {
    return condition;
  }

  public void setCondition(String condition) {
    this.condition = condition;
  }

  public String getExpression() {
    return expression;
  }

  public void setExpression(String expression) {
    this.expression = expression;
  }
}
