package grafiosch.exceptions;

import java.util.Arrays;

/**
 * Represents a single data validation violation with localization support.
 * 
 * <p>
 * This class encapsulates information about a specific validation error that occurred during data processing. It
 * contains all necessary information to generate localized error messages for display to end users, including the field
 * that caused the violation, the message key for internationalization, and any data parameters needed for message
 * formatting.
 * </p>
 * 
 * <h3>Validation Context:</h3>
 * <p>
 * DataViolation instances are typically created during business logic validation and collected into
 * DataViolationException for comprehensive error reporting. This approach allows multiple validation errors to be
 * reported simultaneously rather than stopping at the first encountered error.
 * </p>
 * 
 * <h3>Internationalization Support:</h3>
 * <ul>
 * <li><strong>Message Keys:</strong> Uses resource bundle keys instead of hardcoded messages to support multiple
 * languages</li>
 * <li><strong>Parameter Substitution:</strong> Supports parameterized messages with dynamic data insertion</li>
 * <li><strong>Field Name Translation:</strong> Optional translation of field names for localized field labels</li>
 * </ul>
 * 
 * <h3>Usage Pattern:</h3>
 * <p>
 * Typically used in service layer validation where business rules are enforced. Violations are collected and thrown as
 * part of a DataViolationException to provide comprehensive validation feedback to the presentation layer.
 * </p>
 * 
 * <h3>Error Message Construction:</h3>
 * <p>
 * The violation information is processed by error handlers that use Spring's MessageSource to resolve localized
 * messages based on the user's locale and the provided message key and data parameters.
 * </p>
 */
public class DataViolation {

  private final String field;
  private final String messageKey;
  private final Object[] data;
  private final boolean translateFieldName;

  /**
   * Represents a single data validation violation with localization support.
   * 
   * <p>
   * This class encapsulates information about a specific validation error that occurred during data processing. It
   * contains all necessary information to generate localized error messages for display to end users, including the
   * field that caused the violation, the message key for internationalization, and any data parameters needed for
   * message formatting.
   * </p>
   * 
   * <h3>Validation Context:</h3>
   * <p>
   * DataViolation instances are typically created during business logic validation and collected into
   * DataViolationException for comprehensive error reporting. This approach allows multiple validation errors to be
   * reported simultaneously rather than stopping at the first encountered error.
   * </p>
   * 
   * <h3>Internationalization Support:</h3>
   * <ul>
   * <li><strong>Message Keys:</strong> Uses resource bundle keys instead of hardcoded messages to support multiple
   * languages</li>
   * <li><strong>Parameter Substitution:</strong> Supports parameterized messages with dynamic data insertion</li>
   * <li><strong>Field Name Translation:</strong> Optional translation of field names for localized field labels</li>
   * </ul>
   * 
   * <h3>Usage Pattern:</h3>
   * <p>
   * Typically used in service layer validation where business rules are enforced. Violations are collected and thrown
   * as part of a DataViolationException to provide comprehensive validation feedback to the presentation layer.
   * </p>
   * 
   * <h3>Error Message Construction:</h3>
   * <p>
   * The violation information is processed by error handlers that use Spring's MessageSource to resolve localized
   * messages based on the user's locale and the provided message key and data parameters.
   * </p>
   */
  public DataViolation(final String field, final String messageKey, final Object[] data, boolean translateFieldName) {
    /**
     * The field name associated with this validation violation.
     * 
     * <p>
     * Identifies the specific field or property that failed validation. This field name can optionally be translated to
     * a user-friendly label based on the translateFieldName flag. Field names typically correspond to entity properties
     * or form field identifiers.
     * </p>
     */
    this.field = field;
    /**
     * The message key for internationalized error message lookup.
     * 
     * <p>
     * References a key in the application's message resource bundles that contains the localized error message
     * template. The message template may include parameter placeholders that are replaced with values from the data
     * array during message resolution.
     * </p>
     */
    this.messageKey = messageKey;
    /**
     * Array of data parameters for message template substitution.
     * 
     * <p>
     * Contains the values that will be substituted into the message template placeholders during localized message
     * generation. Parameters are typically inserted using standard message formatting patterns like {0}, {1}, etc.
     * </p>
     */
    this.data = data;
    /**
     * Flag indicating whether the field name should be translated.
     * 
     * <p>
     * When true, the field name will be looked up in the message resource bundles to provide a user-friendly, localized
     * field label. When false, the field name is used as-is, typically for technical field identifiers that don't
     * require translation.
     * </p>
     */
    this.translateFieldName = translateFieldName;
  }

  /**
   * Creates a data violation with a single data parameter.
   * 
   * <p>
   * Convenience constructor for validation violations that only need one parameter for message formatting. This is
   * common for simple validation errors like "field cannot be null" or "value must be greater than {0}". The single
   * data value is automatically wrapped in an array for consistent internal handling.
   * </p>
   * 
   * @param field              the name of the field that caused the violation
   * @param messageKey         the resource bundle key for the error message template
   * @param singleData         the single parameter for message template substitution
   * @param translateFieldName true if the field name should be translated for display
   */
  public DataViolation(final String field, final String messageKey, final Object singleData,
      boolean translateFieldName) {
    this(field, messageKey, new Object[] { singleData }, translateFieldName);
  }

  public Object[] getData() {
    return data;
  }

  public String getMessageKey() {
    return messageKey;
  }

  public String getField() {
    return field;
  }

  public boolean isTranslateFieldName() {
    return translateFieldName;
  }

  @Override
  public String toString() {
    return "DataViolation [field=" + field + ", messageKey=" + messageKey + ", data=" + Arrays.toString(data)
        + ", translateFieldName=" + translateFieldName + "]";
  }

}
