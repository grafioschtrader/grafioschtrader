package grafioschtrader.search;

/**
 * Comparison of an advanced search criterion on a number or a date. A text criterion is always a substring match and a
 * boolean criterion an equality, so neither uses an operator.
 * <p>
 * Mirrored by {@code frontend/src/app/shared/types/advanced.search.operator.ts}; both sides are changed together. The
 * enum holds only its constants, because the mirror guard of the frontend reads this declaration; the comparison is
 * done by {@link AdvancedSearchMatcher}.
 */
public enum AdvancedSearchOperator {
  /** The stored value is smaller than the first comparison value. */
  LESS_THAN,
  /** The stored value is smaller than or equal to the first comparison value. */
  LESS_EQUAL,
  /** The stored value equals the first comparison value; a number is compared exactly. */
  EQUAL,
  /** The stored value is greater than or equal to the first comparison value. */
  GREATER_EQUAL,
  /** The stored value is greater than the first comparison value. */
  GREATER_THAN,
  /** The stored value lies between the first and the second comparison value, both ends included. */
  BETWEEN
}
