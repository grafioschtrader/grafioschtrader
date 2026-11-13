/**
 * Comparison of an advanced search criterion on a number or a date. A text criterion is always a substring match and a
 * boolean criterion an equality, so neither uses an operator. The names are the NLS keys of the option labels.
 *
 * Corresponds to backend: grafioschtrader-common/src/main/java/grafioschtrader/search/AdvancedSearchOperator.java
 */
export enum AdvancedSearchOperator {
  /** The stored value is smaller than the first comparison value. */
  LESS_THAN = 'LESS_THAN',
  /** The stored value is smaller than or equal to the first comparison value. */
  LESS_EQUAL = 'LESS_EQUAL',
  /** The stored value equals the first comparison value. */
  EQUAL = 'EQUAL',
  /** The stored value is greater than or equal to the first comparison value. */
  GREATER_EQUAL = 'GREATER_EQUAL',
  /** The stored value is greater than the first comparison value. */
  GREATER_THAN = 'GREATER_THAN',
  /** The stored value lies between the first and the second comparison value, both ends included. */
  BETWEEN = 'BETWEEN'
}
