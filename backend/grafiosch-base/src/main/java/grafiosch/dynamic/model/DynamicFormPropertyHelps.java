package grafiosch.dynamic.model;

/**
 * Enumeration of specialized form property helpers that provide UI hints and behaviors
 * for dynamic form generation. These helpers influence how input fields are rendered
 * and validated in the user interface, extending beyond basic data type constraints.
 * 
 * <p>These property helpers can be applied to fields to enable specific input behaviors,
 * validation rules, or UI components that enhance the user experience for particular
 * types of data entry.</p>
 */
public enum DynamicFormPropertyHelps {
  /** Input as percentage */
  PERCENTAGE,
  /** Input of a password */
  PASSWORD,
  /** Input of a Email address */
  EMAIL,
  /** Input with select option */
  SELECT_OPTIONS,
  /** Date must be in the future */
  DATE_FUTURE;
}
