package grafiosch.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Parses and validates property values against dynamic validation rules stored as DSL strings. Supports validation
 * rules for numeric values (min, max, enum) and string patterns (regex).
 *
 * <h3>Supported Rule Types:</h3>
 * <ul>
 * <li><strong>min:N</strong> - Minimum numeric value (e.g., "min:1")</li>
 * <li><strong>max:N</strong> - Maximum numeric value (e.g., "max:99")</li>
 * <li><strong>enum:N1,N2,N3</strong> - Value must be one of the specified numbers (e.g., "enum:1,7,12,365")</li>
 * <li><strong>pattern:REGEX</strong> - String must match the regex pattern (e.g., "pattern:^[A-Z]+$")</li>
 * </ul>
 *
 * <h3>Example Usage:</h3>
 *
 * <pre>
 * String inputRule = "min:1,max:99";
 * InputRule rule = InputRule.parse(inputRule);
 * String error = rule.validate(50); // returns null (valid)
 * String error2 = rule.validate(100); // returns error message
 * </pre>
 */
public class InputRule {

  public static final String RULE_MIN = "min";
  public static final String RULE_MAX = "max";
  public static final String RULE_ENUM = "enum";
  public static final String RULE_PATTERN = "pattern";

  private Integer min;
  private Integer max;
  private Set<Integer> enumValues;
  private Pattern pattern;
  private String patternString;

  private InputRule() {
  }

  /**
   * Parses an input rule DSL string into an InputRule object.
   *
   * @param inputRule the DSL string (e.g., "min:1,max:99" or "enum:1,7,12,365")
   * @return parsed InputRule object, or null if inputRule is null or empty
   * @throws IllegalArgumentException if the rule syntax is invalid
   */
  public static InputRule parse(String inputRule) {
    if (inputRule == null || inputRule.isBlank()) {
      return null;
    }

    InputRule rule = new InputRule();
    String[] parts = inputRule.split(",(?=\\w+:)"); // Split on comma followed by rule name

    for (String part : parts) {
      part = part.trim();
      int colonIndex = part.indexOf(':');
      if (colonIndex == -1) {
        throw new IllegalArgumentException("Invalid rule syntax: " + part);
      }

      String ruleName = part.substring(0, colonIndex).toLowerCase();
      String ruleValue = part.substring(colonIndex + 1);

      switch (ruleName) {
      case RULE_MIN:
        rule.min = parseInteger(ruleValue, RULE_MIN);
        break;
      case RULE_MAX:
        rule.max = parseInteger(ruleValue, RULE_MAX);
        break;
      case RULE_ENUM:
        rule.enumValues = parseEnumValues(ruleValue);
        break;
      case RULE_PATTERN:
        rule.patternString = ruleValue;
        try {
          rule.pattern = Pattern.compile(ruleValue);
        } catch (PatternSyntaxException e) {
          throw new IllegalArgumentException("Invalid regex pattern: " + ruleValue, e);
        }
        break;
      default:
        throw new IllegalArgumentException("Unknown rule type: " + ruleName);
      }
    }

    return rule;
  }

  /**
   * Validates an integer value against the parsed rules.
   *
   * @param value the integer value to validate
   * @return error message key if validation fails, null if valid
   */
  public String validate(Integer value) {
    if (value == null) {
      return null;
    }

    if (min != null && value < min) {
      return "gt.input.rule.min.violation";
    }

    if (max != null && value > max) {
      return "gt.input.rule.max.violation";
    }

    if (enumValues != null && !enumValues.contains(value)) {
      return "gt.input.rule.enum.violation";
    }

    return null;
  }

  /**
   * Validates a string value against the parsed rules (pattern validation).
   *
   * @param value the string value to validate
   * @return error message key if validation fails, null if valid
   */
  public String validate(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }

    if (pattern != null && !pattern.matcher(value).matches()) {
      return "gt.input.rule.pattern.violation";
    }

    return null;
  }

  /**
   * Returns a human-readable description of the validation rules for display purposes.
   *
   * @return formatted rule description
   */
  public String getDescription() {
    List<String> descriptions = new ArrayList<>();

    if (min != null) {
      descriptions.add(RULE_MIN + ": " + min);
    }
    if (max != null) {
      descriptions.add(RULE_MAX + ": " + max);
    }
    if (enumValues != null) {
      descriptions.add(RULE_ENUM + ": " + enumValues.stream().sorted().map(String::valueOf)
          .collect(Collectors.joining(", ")));
    }
    if (patternString != null) {
      descriptions.add(RULE_PATTERN + ": " + patternString);
    }

    return String.join(" | ", descriptions);
  }

  public Integer getMin() {
    return min;
  }

  public Integer getMax() {
    return max;
  }

  public Set<Integer> getEnumValues() {
    return enumValues;
  }

  public String getPatternString() {
    return patternString;
  }

  private static Integer parseInteger(String value, String ruleName) {
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid integer value for " + ruleName + ": " + value);
    }
  }

  private static Set<Integer> parseEnumValues(String value) {
    try {
      return Arrays.stream(value.split(",")).map(String::trim).map(Integer::parseInt).collect(Collectors.toSet());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid enum values: " + value);
    }
  }
}
