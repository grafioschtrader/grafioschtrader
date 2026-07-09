package grafioschtrader.types;

/**
 * Kind of manual tax year correction applied to a security dividends position. Serialized by name to the
 * frontend, which uses it to highlight the affected report cells.
 */
public enum TaxYearCorrectionType {
  /** The position's computed taxableAmountMC replaced the ICTax payment total. */
  TAXABLE_AMOUNT,
  /** A directly entered taxable income replaced the ICTax payment total. */
  DIRECT_VALUE,
  /** A correction record exists but only carries a comment; no value was changed. */
  COMMENT_ONLY
}
