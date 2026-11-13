package grafioschtrader.search;

/**
 * The kind of value a field of the advanced search holds. It decides which operator and which comparison values a
 * criterion needs.
 */
public enum AdvancedSearchValueType {
  /** Decimal or integer number; compared with an {@link AdvancedSearchOperator}. */
  NUMBER,
  /** Calendar day; compared with an {@link AdvancedSearchOperator}. */
  DATE,
  /** Free text; matched as a case-insensitive substring. */
  TEXT,
  /** True or false; matched by equality. */
  BOOLEAN
}
