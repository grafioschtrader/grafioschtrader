package grafioschtrader.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import grafiosch.exceptions.DataViolationException;

/**
 * Rules of the advanced search that need no database: which criteria are valid, how stored text is read as a value and
 * when a value satisfies a criterion.
 */
class AdvancedSearchMatcherTest {

  private static final Map<String, AdvancedSearchField> CATALOGUE = Map.of("U1",
      new AdvancedSearchField("U1", "Number", AdvancedSearchValueType.NUMBER, false), "U2",
      new AdvancedSearchField("U2", "Date", AdvancedSearchValueType.DATE, false), "U3",
      new AdvancedSearchField("U3", "Text", AdvancedSearchValueType.TEXT, false), "U4",
      new AdvancedSearchField("U4", "Flag", AdvancedSearchValueType.BOOLEAN, false));

  private static AdvancedSearchCriterion number(AdvancedSearchOperator operator, Double from, Double to) {
    AdvancedSearchCriterion criterion = new AdvancedSearchCriterion();
    criterion.setField("U1");
    criterion.setOperator(operator);
    criterion.setNumber(from);
    criterion.setNumberTo(to);
    return criterion;
  }

  private static AdvancedSearchCriterion date(AdvancedSearchOperator operator, LocalDate from, LocalDate to) {
    AdvancedSearchCriterion criterion = new AdvancedSearchCriterion();
    criterion.setField("U2");
    criterion.setOperator(operator);
    criterion.setDate(from);
    criterion.setDateTo(to);
    return criterion;
  }

  private static AdvancedSearchCriterion text(String text) {
    AdvancedSearchCriterion criterion = new AdvancedSearchCriterion();
    criterion.setField("U3");
    criterion.setText(text);
    return criterion;
  }

  private static AdvancedSearchCriterion flag(Boolean flag) {
    AdvancedSearchCriterion criterion = new AdvancedSearchCriterion();
    criterion.setField("U4");
    criterion.setFlag(flag);
    return criterion;
  }

  private static String violationKey(DataViolationException e) {
    return e.getDataViolation().getFirst().getMessageKey();
  }

  @Test
  @DisplayName("Complete criteria of every value type are valid, zero and false are values")
  void completeCriteriaAreValid() {
    assertThatCode(() -> AdvancedSearchMatcher.validate(List.of(number(AdvancedSearchOperator.LESS_THAN, 0.0, null),
        date(AdvancedSearchOperator.BETWEEN, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)), text("a"),
        flag(false)), CATALOGUE)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("More than five criteria, or one field twice, is refused as too many")
  void tooManyCriteria() {
    List<AdvancedSearchCriterion> sixTimes = Collections.nCopies(6, text("a"));
    assertThatThrownBy(() -> AdvancedSearchMatcher.validate(sixTimes, CATALOGUE))
        .isInstanceOfSatisfying(DataViolationException.class,
            e -> assertThat(violationKey(e)).isEqualTo("gt.advanced.search.too.many"));
    assertThatThrownBy(() -> AdvancedSearchMatcher.validate(List.of(text("a"), text("b")), CATALOGUE))
        .isInstanceOfSatisfying(DataViolationException.class,
            e -> assertThat(violationKey(e)).isEqualTo("gt.advanced.search.too.many"));
  }

  @Test
  @DisplayName("A field that is not in the catalogue of the user is refused, also an empty list entry")
  void unknownField() {
    AdvancedSearchCriterion other = text("a");
    other.setField("U99");
    assertThatThrownBy(() -> AdvancedSearchMatcher.validate(List.of(other), CATALOGUE))
        .isInstanceOfSatisfying(DataViolationException.class,
            e -> assertThat(violationKey(e)).isEqualTo("gt.advanced.search.unknown.field"));
    assertThatThrownBy(() -> AdvancedSearchMatcher.validate(List.of(new AdvancedSearchCriterion()), CATALOGUE))
        .isInstanceOf(DataViolationException.class);
  }

  @Test
  @DisplayName("Incomplete or inconsistent values are refused, never dropped")
  void invalidValues() {
    AdvancedSearchCriterion numberWithText = number(AdvancedSearchOperator.LESS_THAN, 1.0, null);
    numberWithText.setText("x");
    List<AdvancedSearchCriterion> invalid = List.of(number(null, 1.0, null),
        number(AdvancedSearchOperator.LESS_THAN, null, null), number(AdvancedSearchOperator.LESS_THAN, 1.0, 2.0),
        number(AdvancedSearchOperator.BETWEEN, 1.0, null), number(AdvancedSearchOperator.BETWEEN, 2.0, 1.0),
        number(AdvancedSearchOperator.LESS_THAN, Double.NaN, null),
        number(AdvancedSearchOperator.LESS_THAN, Double.POSITIVE_INFINITY, null), numberWithText,
        date(AdvancedSearchOperator.EQUAL, null, null),
        date(AdvancedSearchOperator.BETWEEN, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)), text(null),
        text("  "), text("x".repeat(AdvancedSearchCriterion.MAX_TEXT_LENGTH + 1)), flag(null));
    for (AdvancedSearchCriterion criterion : invalid) {
      assertThatThrownBy(() -> AdvancedSearchMatcher.validate(List.of(criterion), CATALOGUE))
          .as("criterion of %s", criterion.getField())
          .isInstanceOfSatisfying(DataViolationException.class,
              e -> assertThat(violationKey(e)).isEqualTo("gt.advanced.search.invalid.value"));
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = { "abc", "12abc", "NaN", "Infinity", "-Infinity", " " })
  @DisplayName("Stored text that is not a finite number is no value, so it never meets a range that contains zero")
  void unreadableNumberIsNoValue(String stored) {
    assertThat(AdvancedSearchMatcher.parseUdfValue(stored, AdvancedSearchValueType.NUMBER)).isNull();
  }

  @Test
  @DisplayName("A number is read from a string and from a JSON number alike")
  void numberReading() {
    assertThat(AdvancedSearchMatcher.parseUdfValue("7.50", AdvancedSearchValueType.NUMBER)).isEqualTo(7.5);
    assertThat(AdvancedSearchMatcher.parseUdfValue("1e2", AdvancedSearchValueType.NUMBER)).isEqualTo(100.0);
    assertThat(AdvancedSearchMatcher.parseUdfValue("-3", AdvancedSearchValueType.NUMBER)).isEqualTo(-3.0);
  }

  @Test
  @DisplayName("A date is the first ten characters of a date or an ISO date-time, without zone conversion")
  void dateReading() {
    assertThat(AdvancedSearchMatcher.parseUdfValue("2026-03-15", AdvancedSearchValueType.DATE))
        .isEqualTo(LocalDate.of(2026, 3, 15));
    assertThat(AdvancedSearchMatcher.parseUdfValue("2026-03-15T23:30:00+05:00", AdvancedSearchValueType.DATE))
        .isEqualTo(LocalDate.of(2026, 3, 15));
    assertThat(AdvancedSearchMatcher.parseUdfValue("2026-3-5", AdvancedSearchValueType.DATE)).isNull();
    assertThat(AdvancedSearchMatcher.parseUdfValue("not a date at all", AdvancedSearchValueType.DATE)).isNull();
  }

  @Test
  @DisplayName("A JSON boolean arrives as 1 or 0 from MariaDB, the words true and false are read as well")
  void booleanReading() {
    assertThat(AdvancedSearchMatcher.parseUdfValue("1", AdvancedSearchValueType.BOOLEAN)).isEqualTo(true);
    assertThat(AdvancedSearchMatcher.parseUdfValue("0", AdvancedSearchValueType.BOOLEAN)).isEqualTo(false);
    assertThat(AdvancedSearchMatcher.parseUdfValue("true", AdvancedSearchValueType.BOOLEAN)).isEqualTo(true);
    assertThat(AdvancedSearchMatcher.parseUdfValue("false", AdvancedSearchValueType.BOOLEAN)).isEqualTo(false);
    assertThat(AdvancedSearchMatcher.parseUdfValue("yes", AdvancedSearchValueType.BOOLEAN)).isNull();
    assertThat(AdvancedSearchMatcher.parseUdfValue("2", AdvancedSearchValueType.BOOLEAN)).isNull();
  }

  @Test
  @DisplayName("The value 20 is excluded by less than, included by less or equal, and both ends of between count")
  void operatorsAtTheBoundary() {
    assertThat(matchesNumber(AdvancedSearchOperator.LESS_THAN, 20.0, null, 20.0)).isFalse();
    assertThat(matchesNumber(AdvancedSearchOperator.LESS_EQUAL, 20.0, null, 20.0)).isTrue();
    assertThat(matchesNumber(AdvancedSearchOperator.EQUAL, 20.0, null, 20.0)).isTrue();
    assertThat(matchesNumber(AdvancedSearchOperator.GREATER_THAN, 20.0, null, 20.0)).isFalse();
    assertThat(matchesNumber(AdvancedSearchOperator.GREATER_EQUAL, 20.0, null, 20.0)).isTrue();
    assertThat(matchesNumber(AdvancedSearchOperator.BETWEEN, 20.0, 30.0, 20.0)).isTrue();
    assertThat(matchesNumber(AdvancedSearchOperator.BETWEEN, 10.0, 20.0, 20.0)).isTrue();
    assertThat(matchesNumber(AdvancedSearchOperator.BETWEEN, 10.0, 20.0, 20.5)).isFalse();
    assertThat(matchesNumber(AdvancedSearchOperator.LESS_THAN, 20.0, null, -5.0)).as("a negative value is below 20")
        .isTrue();
  }

  private static boolean matchesNumber(AdvancedSearchOperator operator, Double from, Double to, double value) {
    return AdvancedSearchMatcher.matches(number(operator, from, to), AdvancedSearchValueType.NUMBER, value);
  }

  @Test
  @DisplayName("Dates are compared as calendar days")
  void dateMatching() {
    LocalDate day = LocalDate.of(2026, 3, 15);
    assertThat(AdvancedSearchMatcher.matches(date(AdvancedSearchOperator.BETWEEN, day, day.plusDays(1)),
        AdvancedSearchValueType.DATE, day)).isTrue();
    assertThat(AdvancedSearchMatcher.matches(date(AdvancedSearchOperator.GREATER_THAN, day, null),
        AdvancedSearchValueType.DATE, day)).isFalse();
  }

  @Test
  @DisplayName("A text is a case-insensitive substring in which percent and underscore are ordinary characters")
  void textMatching() {
    assertThat(AdvancedSearchMatcher.matches(text("100%"), AdvancedSearchValueType.TEXT, "Fully 100% sure")).isTrue();
    assertThat(AdvancedSearchMatcher.matches(text("100%"), AdvancedSearchValueType.TEXT, "100x sure")).isFalse();
    assertThat(AdvancedSearchMatcher.matches(text("A_C"), AdvancedSearchValueType.TEXT, "abc")).isFalse();
    assertThat(AdvancedSearchMatcher.matches(text("SURE"), AdvancedSearchValueType.TEXT, "very sure")).isTrue();
  }

  @Test
  @DisplayName("A flag matches only its own value, no value and a value of another type match nothing")
  void flagAndMissingValue() {
    assertThat(AdvancedSearchMatcher.matches(flag(false), AdvancedSearchValueType.BOOLEAN, Boolean.FALSE)).isTrue();
    assertThat(AdvancedSearchMatcher.matches(flag(false), AdvancedSearchValueType.BOOLEAN, Boolean.TRUE)).isFalse();
    assertThat(AdvancedSearchMatcher.matches(flag(true), AdvancedSearchValueType.BOOLEAN, null)).isFalse();
    assertThat(AdvancedSearchMatcher.matches(number(AdvancedSearchOperator.LESS_THAN, 1.0, null),
        AdvancedSearchValueType.NUMBER, "0")).isFalse();
  }
}
