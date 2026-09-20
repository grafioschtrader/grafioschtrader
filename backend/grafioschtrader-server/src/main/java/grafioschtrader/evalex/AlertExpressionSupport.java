package grafioschtrader.evalex;

import java.math.BigDecimal;
import java.util.*;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.config.ExpressionConfiguration;
import com.ezylang.evalex.parser.Token.TokenType;

import grafioschtrader.entities.Historyquote;

/** Shared alert expression contract. Validation parses every branch without loading prices or evaluating a signal. */
public final class AlertExpressionSupport {
  public static final Set<String> VARIABLES = Set.of("PRICE", "PREVCLOSE", "OPEN", "HIGH", "LOW", "VOLUME");
  private static final Set<String> INDICATORS = Set.of("SMA", "EMA", "RSI");
  private static final Set<String> FUNCTIONS = Set.of("SMA", "EMA", "RSI", "IF", "ABS", "ACOS", "ACOSH", "ASIN",
      "ASINH", "ATAN", "ATAN2", "ATANH", "CEILING", "COS", "COSH", "COT", "CSC", "DEG", "FACT", "FLOOR", "LOG", "LOG10",
      "MAX", "MIN", "NOT", "RAD", "ROUND", "SEC", "SIN", "SINH", "SQRT", "SUM", "TAN", "TANH");

  private AlertExpressionSupport() {
  }

  public static Expression create(String text, List<Historyquote> history) {
    return new Expression(text,
        ExpressionConfiguration.defaultConfiguration().withAdditionalFunctions(
            Map.entry("SMA", new SmaFunction(history)), Map.entry("EMA", new EmaFunction(history)),
            Map.entry("RSI", new RsiFunction(history))));
  }

  public static void validate(String text) {
    try {
      if (text == null || text.isBlank() || text.length() > 500)
        throw new IllegalArgumentException("Expression must contain 1–500 characters");
      Expression expression = create(text, List.of());
      expression.validate();
      for (String variable : expression.getUsedVariables()) {
        if (!VARIABLES.contains(variable.toUpperCase(Locale.ROOT)))
          throw new IllegalArgumentException("Unknown variable: " + variable);
      }
      for (var node : expression.getAllASTNodes()) {
        var token = node.getToken();
        if (token.getType() == TokenType.STRING_LITERAL || token.getType() == TokenType.ARRAY_INDEX
            || token.getType() == TokenType.STRUCTURE_SEPARATOR)
          throw new IllegalArgumentException("Alert expressions must use numeric or boolean values");
        if (token.getType() == TokenType.FUNCTION) {
          String function = token.getValue().toUpperCase(Locale.ROOT);
          if (!FUNCTIONS.contains(function))
            throw new IllegalArgumentException("Unsupported alert function: " + function);
          if (INDICATORS.contains(function)) {
            if (node.getParameters().size() != 1
                || node.getParameters().getFirst().getToken().getType() != TokenType.NUMBER_LITERAL)
              throw new IllegalArgumentException("Indicator period must be a constant integer from 1 to 999");
            period(new BigDecimal(node.getParameters().getFirst().getToken().getValue()));
          }
        }
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid alert expression: " + e.getMessage(), e);
    }
  }

  /** Avoid truncating fractional periods or accepting an unbounded history request. */
  public static int period(BigDecimal number) {
    int value = number.intValueExact();
    if (value < 1 || value > 999)
      throw new IllegalArgumentException("Indicator period must be from 1 to 999");
    return value;
  }
}
