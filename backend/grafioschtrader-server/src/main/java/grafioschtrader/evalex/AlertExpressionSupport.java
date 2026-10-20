package grafioschtrader.evalex;

import java.math.BigDecimal;
import java.util.*;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.config.ExpressionConfiguration;
import com.ezylang.evalex.parser.ASTNode;
import com.ezylang.evalex.parser.Token.TokenType;

import grafioschtrader.entities.Historyquote;
import grafioschtrader.types.IndicatorResultType;

/** One parsed expression contract for alerts and watchlist columns, including static input and result types. */
public final class AlertExpressionSupport {
  public static final int SERIES_OBSERVATIONS = 1200;
  public static final Set<String> VARIABLES = Set.of("PRICE", "PREVCLOSE", "OPEN", "HIGH", "LOW", "VOLUME");
  /**
   * Fundamental base values of direct equities, bound only from a selected value of {@code security_fundamental} and
   * only where {@link IndicatorData#FUNDAMENTALS} is available.
   */
  public static final Set<String> FUNDAMENTAL_VARIABLES = Set.of("EPS", "EPS_FY", "BVPS", "SPS", "SHARES",
      "NET_DEBT");
  private static final Set<String> FUNCTIONS = Set.of("IF", "ABS", "ACOS", "ACOSH", "ASIN", "ASINH", "ATAN", "ATAN2",
      "ATANH", "CEILING", "COS", "COSH", "COT", "CSC", "DEG", "FACT", "FLOOR", "LOG", "LOG10", "MAX", "MIN", "NOT",
      "RAD", "ROUND", "SEC", "SIN", "SINH", "SQRT", "SUM", "TAN", "TANH");

  public record Descriptor(int arguments, int min, int max, IndicatorData data) {
  }

  public static final Map<String, Descriptor> INDICATORS = descriptors();

  private AlertExpressionSupport() {
  }

  private static Map<String, Descriptor> descriptors() {
    Map<String, Descriptor> map = new LinkedHashMap<>();
    for (String name : List.of("SMA", "EMA", "RSI", "HIGHEST", "LOWEST", "ZSCORE", "MAXDD", "ATR"))
      map.put(name, new Descriptor(1, 1, 999, IndicatorData.SERIES));
    map.put("CLOSE", new Descriptor(1, 0, 999, IndicatorData.SERIES));
    map.put("VOLA", new Descriptor(1, 2, 999, IndicatorData.SERIES));
    map.put("RS", new Descriptor(1, 1, 999, IndicatorData.BENCHMARK));
    map.put("BETA", new Descriptor(1, 2, 999, IndicatorData.BENCHMARK));
    map.put("DIVGROWTH", new Descriptor(1, 1, 20, IndicatorData.DIVIDENDS));
    map.put("DIVYIELD", new Descriptor(0, 0, 0, IndicatorData.DIVIDENDS));
    map.put("DIVYEARS", new Descriptor(0, 0, 0, IndicatorData.DIVIDENDS));
    return Collections.unmodifiableMap(map);
  }

  public static Expression create(String text, List<Historyquote> history) {
    return create(text, new IndicatorFunctionSet(IndicatorSeries.history(history)));
  }

  public static Expression create(String text, IndicatorFunctionSet functions) {
    var configuration = ExpressionConfiguration.defaultConfiguration();
    for (var function : functions.functions().entrySet())
      configuration = configuration.withAdditionalFunctions(function);
    return new Expression(text, configuration);
  }

  public static void validate(String text) {
    validate(text, EnumSet.of(IndicatorData.SERIES));
  }

  public static void validate(String text, Set<IndicatorData> available) {
    Expression expression = parsed(text);
    try {
      for (String variable : expression.getUsedVariables()) {
        String name = variable.toUpperCase(Locale.ROOT);
        if (FUNDAMENTAL_VARIABLES.contains(name)) {
          if (!available.contains(IndicatorData.FUNDAMENTALS))
            throw new AlertExpressionException("ALERT_EXPRESSION_DATA_NOT_AVAILABLE", variable);
        } else if (!VARIABLES.contains(name))
          throw new AlertExpressionException("ALERT_EXPRESSION_UNKNOWN_VARIABLE", variable);
      }
      for (var node : expression.getAllASTNodes()) {
        var token = node.getToken();
        if (token.getType() == TokenType.STRING_LITERAL || token.getType() == TokenType.ARRAY_INDEX
            || token.getType() == TokenType.STRUCTURE_SEPARATOR)
          throw new AlertExpressionException("ALERT_EXPRESSION_NOT_NUMERIC");
        if (token.getType() == TokenType.FUNCTION) {
          String name = token.getValue().toUpperCase(Locale.ROOT);
          Descriptor descriptor = INDICATORS.get(name);
          if (descriptor != null) {
            if (!available.contains(descriptor.data()))
              throw new AlertExpressionException("ALERT_EXPRESSION_DATA_NOT_AVAILABLE", name);
            argument(node, descriptor);
          } else if (!FUNCTIONS.contains(name))
            throw new AlertExpressionException("ALERT_EXPRESSION_UNKNOWN_FUNCTION", name);
        }
        type(node); // also reject mixed IF branches nested below an arithmetic root
      }
    } catch (AlertExpressionException e) {
      throw e;
    } catch (Exception e) {
      throw new AlertExpressionException("ALERT_EXPRESSION_SYNTAX");
    }
  }

  private static Expression parsed(String text) {
    if (text == null || text.isBlank() || text.length() > 500)
      throw new AlertExpressionException("ALERT_EXPRESSION_LENGTH");
    try {
      var expression = create(text, List.of());
      expression.validate();
      return expression;
    } catch (com.ezylang.evalex.parser.ParseException e) {
      if (e.getMessage().startsWith("Undefined function '"))
        throw new AlertExpressionException("ALERT_EXPRESSION_UNKNOWN_FUNCTION", e.getTokenString());
      throw new AlertExpressionException("ALERT_EXPRESSION_SYNTAX");
    } catch (Exception e) {
      throw new AlertExpressionException("ALERT_EXPRESSION_SYNTAX");
    }
  }

  private static int argument(ASTNode node, Descriptor d) {
    try {
      if (node.getParameters().size() != d.arguments())
        throw new ArithmeticException();
      if (d.arguments() == 0)
        return 0;
      var token = node.getParameters().getFirst().getToken();
      if (token.getType() != TokenType.NUMBER_LITERAL)
        throw new ArithmeticException();
      int value = new BigDecimal(token.getValue()).intValueExact();
      if (value < d.min() || value > d.max())
        throw new ArithmeticException();
      return value;
    } catch (ArithmeticException e) {
      throw new AlertExpressionException("ALERT_EXPRESSION_ARGUMENT_RANGE", node.getToken().getValue(), d.min(),
          d.max());
    }
  }

  public static Set<IndicatorData> requiredData(String text) {
    Set<IndicatorData> required = EnumSet.noneOf(IndicatorData.class);
    try {
      var expression = parsed(text);
      if (expression.getUsedVariables().stream()
          .anyMatch(v -> FUNDAMENTAL_VARIABLES.contains(v.toUpperCase(Locale.ROOT))))
        required.add(IndicatorData.FUNDAMENTALS);
      for (var node : expression.getAllASTNodes()) {
        if (node.getToken().getType() == TokenType.FUNCTION) {
          var d = INDICATORS.get(node.getToken().getValue().toUpperCase(Locale.ROOT));
          if (d != null)
            required.add(d.data());
        }
      }
      return required;
    } catch (AlertExpressionException e) {
      throw e;
    } catch (Exception e) {
      throw new AlertExpressionException("ALERT_EXPRESSION_SYNTAX");
    }
  }

  /** The fundamental variables an expression reads, upper case and sorted, for the cell tooltip. */
  public static List<String> fundamentalVariables(String text) {
    try {
      return parsed(text).getUsedVariables().stream().map(v -> v.toUpperCase(Locale.ROOT))
          .filter(FUNDAMENTAL_VARIABLES::contains).distinct().sorted().toList();
    } catch (AlertExpressionException e) {
      throw e;
    } catch (Exception e) {
      throw new AlertExpressionException("ALERT_EXPRESSION_SYNTAX");
    }
  }

  /** Earliest required dividend date, so unrelated columns do not cause additional history reads. */
  public static java.time.LocalDate dividendFrom(String text, java.time.LocalDate today) {
    java.time.LocalDate from = today;
    try {
      for (var node : parsed(text).getAllASTNodes()) {
        String name = node.getToken().getValue().toUpperCase(Locale.ROOT);
        java.time.LocalDate date = switch (name) {
        case "DIVYIELD" -> today.minusDays(364);
        case "DIVYEARS" -> java.time.LocalDate.of(today.getYear() - 21, 1, 1);
        case "DIVGROWTH" -> java.time.LocalDate.of(today.getYear() - 1 - argument(node, INDICATORS.get(name)), 1, 1);
        default -> today;
        };
        if (date.isBefore(from))
          from = date;
      }
      return from;
    } catch (Exception e) {
      throw new AlertExpressionException("ALERT_EXPRESSION_SYNTAX");
    }
  }

  public static IndicatorResultType resultType(String text) {
    try {
      return type(parsed(text).getAbstractSyntaxTree());
    } catch (AlertExpressionException e) {
      throw e;
    } catch (Exception e) {
      throw new AlertExpressionException("ALERT_EXPRESSION_SYNTAX");
    }
  }

  private static IndicatorResultType type(ASTNode node) {
    String value = node.getToken().getValue().toUpperCase(Locale.ROOT);
    if (Set.of("<", "<=", ">", ">=", "=", "==", "!=", "<>", "&&", "||", "AND", "OR", "!", "NOT", "TRUE", "FALSE")
        .contains(value))
      return IndicatorResultType.BOOLEAN;
    if (value.equals("IF")) {
      var first = type(node.getParameters().get(1));
      if (first != type(node.getParameters().get(2)))
        throw new AlertExpressionException("ALERT_EXPRESSION_MIXED_RESULT");
      return first;
    }
    return IndicatorResultType.NUMBER;
  }

  public static int period(BigDecimal number) {
    int value = number.intValueExact();
    if (value < 1 || value > 999)
      throw new AlertExpressionException("ALERT_EXPRESSION_ARGUMENT_RANGE", "period", 1, 999);
    return value;
  }
}
