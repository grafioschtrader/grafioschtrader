package grafioschtrader.search;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import grafiosch.exceptions.DataViolationException;

/**
 * Validates the advanced criteria of a search request and decides whether a stored value satisfies one of them. It has
 * no dependency on a repository, so the rules can be tested without a database.
 * <p>
 * The values of user-defined fields arrive as text (the result of {@code JSON_VALUE}) because they are stored with
 * mixed JSON types: a value typed by a user is a string, a value written by an automatic fill may be a number. A value
 * that cannot be read is no value and never matches; in particular an empty or partly numeric string is not treated as
 * a number.
 */
public final class AdvancedSearchMatcher {

  /** Field key of the validation errors; it has a label in the message bundle. */
  public static final String VIOLATION_FIELD = "advanced.search";

  private static final int DATE_TEXT_LENGTH = 10;

  private AdvancedSearchMatcher() {
  }

  /**
   * Checks the criteria of a request against the fields offered to the user.
   *
   * @param criteria  the criteria of the request, may be empty
   * @param catalogue the fields the user may use, by key
   * @throws DataViolationException if there are too many criteria, a field is used twice, a field is not offered, or
   *                                the values do not fit the value type of the field
   */
  public static void validate(List<AdvancedSearchCriterion> criteria,
      Map<String, AdvancedSearchField> catalogue) {
    if (criteria.size() > SecuritycurrencySearch.MAX_ADVANCED_CRITERIA) {
      throw violation("gt.advanced.search.too.many", SecuritycurrencySearch.MAX_ADVANCED_CRITERIA);
    }
    Set<String> used = new HashSet<>();
    for (AdvancedSearchCriterion criterion : criteria) {
      AdvancedSearchField field = criterion == null || criterion.getField() == null ? null
          : catalogue.get(criterion.getField());
      if (field == null) {
        throw violation("gt.advanced.search.unknown.field");
      }
      if (!used.add(field.getKey())) {
        throw violation("gt.advanced.search.too.many", SecuritycurrencySearch.MAX_ADVANCED_CRITERIA);
      }
      if (!isValidFor(criterion, field.getValueType())) {
        throw violation("gt.advanced.search.invalid.value", field.getLabel());
      }
    }
  }

  private static DataViolationException violation(String messageKey, Object... args) {
    return new DataViolationException(VIOLATION_FIELD, messageKey, args);
  }

  /**
   * Whether a criterion carries exactly the attributes its value type needs.
   *
   * @param criterion the criterion
   * @param valueType the value type of its field
   * @return true if the operator and the comparison values are complete and consistent
   */
  static boolean isValidFor(AdvancedSearchCriterion criterion, AdvancedSearchValueType valueType) {
    boolean noNumber = criterion.getNumber() == null && criterion.getNumberTo() == null;
    boolean noDate = criterion.getDate() == null && criterion.getDateTo() == null;
    boolean noText = criterion.getText() == null;
    boolean noFlag = criterion.getFlag() == null;
    boolean noOperator = criterion.getOperator() == null;
    return switch (valueType) {
    case NUMBER -> noDate && noText && noFlag && isValidNumber(criterion);
    case DATE -> noNumber && noText && noFlag && isValidDate(criterion);
    case TEXT -> noNumber && noDate && noFlag && noOperator && isValidText(criterion.getText());
    case BOOLEAN -> noNumber && noDate && noText && noOperator && criterion.getFlag() != null;
    };
  }

  private static boolean isValidNumber(AdvancedSearchCriterion criterion) {
    Double from = criterion.getNumber();
    Double to = criterion.getNumberTo();
    if (criterion.getOperator() == null || from == null || !Double.isFinite(from)) {
      return false;
    }
    if (criterion.getOperator() != AdvancedSearchOperator.BETWEEN) {
      return to == null;
    }
    return to != null && Double.isFinite(to) && to >= from;
  }

  private static boolean isValidDate(AdvancedSearchCriterion criterion) {
    LocalDate from = criterion.getDate();
    LocalDate to = criterion.getDateTo();
    if (criterion.getOperator() == null || from == null) {
      return false;
    }
    if (criterion.getOperator() != AdvancedSearchOperator.BETWEEN) {
      return to == null;
    }
    return to != null && !to.isBefore(from);
  }

  private static boolean isValidText(String text) {
    return text != null && !text.isBlank() && text.length() <= AdvancedSearchCriterion.MAX_TEXT_LENGTH;
  }

  /**
   * Reads the stored text of a user-defined field as a value of the given type.
   *
   * @param jsonValue the text of the stored JSON value, null if there is none
   * @param valueType the value type of the field
   * @return a {@code Double}, {@code LocalDate}, {@code String} or {@code Boolean}, or null if there is no readable
   *         value
   */
  public static Object parseUdfValue(String jsonValue, AdvancedSearchValueType valueType) {
    if (jsonValue == null) {
      return null;
    }
    return switch (valueType) {
    case NUMBER -> parseNumber(jsonValue);
    case DATE -> parseDate(jsonValue);
    case TEXT -> jsonValue;
    case BOOLEAN -> parseBoolean(jsonValue);
    };
  }

  /**
   * MariaDB renders a JSON boolean as 1 or 0, while a string that holds the word keeps it, so both forms are read.
   */
  private static Boolean parseBoolean(String text) {
    return switch (text) {
    case "true", "1" -> Boolean.TRUE;
    case "false", "0" -> Boolean.FALSE;
    default -> null;
    };
  }

  private static Double parseNumber(String text) {
    try {
      double number = Double.parseDouble(text);
      return Double.isFinite(number) ? number : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static LocalDate parseDate(String text) {
    if (text.length() < DATE_TEXT_LENGTH) {
      return null;
    }
    try {
      return LocalDate.parse(text.substring(0, DATE_TEXT_LENGTH));
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /**
   * Decides whether a value satisfies a criterion.
   *
   * @param criterion a criterion that passed {@link #validate}
   * @param valueType the value type of its field
   * @param value     the value of the instrument as returned by {@link #parseUdfValue}, or the value of an indicator
   *                  cell; null or a value of another type does not match
   * @return true if the value satisfies the criterion
   */
  public static boolean matches(AdvancedSearchCriterion criterion, AdvancedSearchValueType valueType, Object value) {
    return switch (valueType) {
    case NUMBER -> value instanceof Double number
        && compare(criterion.getOperator(), number, criterion.getNumber(), criterion.getNumberTo());
    case DATE -> value instanceof LocalDate date
        && compare(criterion.getOperator(), date, criterion.getDate(), criterion.getDateTo());
    case TEXT -> value instanceof String text
        && text.toLowerCase(Locale.ROOT).contains(criterion.getText().toLowerCase(Locale.ROOT));
    case BOOLEAN -> value instanceof Boolean flag && flag.equals(criterion.getFlag());
    };
  }

  /**
   * Applies an operator to comparable values.
   *
   * @param <T>      type of the compared values
   * @param operator the comparison
   * @param value    the stored value of the instrument
   * @param first    the first comparison value
   * @param second   the second comparison value, used only by {@link AdvancedSearchOperator#BETWEEN}
   * @return true if the stored value satisfies the comparison
   */
  private static <T extends Comparable<? super T>> boolean compare(AdvancedSearchOperator operator, T value, T first,
      T second) {
    int c = value.compareTo(first);
    return switch (operator) {
    case LESS_THAN -> c < 0;
    case LESS_EQUAL -> c <= 0;
    case EQUAL -> c == 0;
    case GREATER_EQUAL -> c >= 0;
    case GREATER_THAN -> c > 0;
    case BETWEEN -> c >= 0 && value.compareTo(second) <= 0;
    };
  }

  /**
   * The form in which a matched value is returned to the client: a date as ISO text, everything else unchanged.
   *
   * @param value the matched value
   * @return the value for the JSON response
   */
  public static Object displayValue(Object value) {
    return value instanceof LocalDate date ? date.toString() : value;
  }
}
