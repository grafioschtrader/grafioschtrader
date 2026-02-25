package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    A single fee rule with an EvalEx boolean condition and a numeric expression for fee calculation.
    String variables: instrument (DIRECT_INVESTMENT, ETF, MUTUAL_FUND, ...), assetclass (EQUITIES,
    FIXED_INCOME, ...), mic, currency. Numeric variables: tradeValue, units, fixedAssets, tradeDirection.
    Legacy numeric aliases: specInvestInstrument, categoryType.""")
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
