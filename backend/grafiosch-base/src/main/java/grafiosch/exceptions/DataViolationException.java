package grafiosch.exceptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime exception for collecting and reporting multiple data validation violations.
 * 
 * <p>
 * This exception extends RuntimeException to provide a mechanism for accumulating multiple validation errors during
 * business logic processing and reporting them all at once. Instead of stopping at the first validation error, this
 * approach allows the application to collect all violations and provide comprehensive feedback to users.
 * </p>
 * 
 * <h3>Validation Strategy:</h3>
 * <p>
 * The exception supports a "collect and report" validation strategy where business logic can continue checking multiple
 * fields and rules, accumulating violations as they are discovered. This provides a better user experience by showing
 * all validation problems simultaneously rather than requiring multiple correction cycles.
 * </p>
 * 
 * <h3>Internationalization Support:</h3>
 * <ul>
 * <li><strong>Locale Management:</strong> Optionally stores the user's locale for proper message localization during
 * error handling</li>
 * <li><strong>Message Key Support:</strong> Each violation uses message keys that can be resolved to localized
 * messages</li>
 * <li><strong>Parameter Substitution:</strong> Supports dynamic parameter insertion for contextual error messages</li>
 * </ul>
 * 
 * <h3>Error Handling Integration:</h3>
 * <p>
 * This exception is typically caught by REST error handlers that convert the collected violations into appropriate HTTP
 * responses with localized error messages. The exception provides all necessary information for generating
 * user-friendly validation feedback.
 * </p>
 * 
 * <h3>Usage Pattern:</h3>
 * <p>
 * Common usage involves creating an exception instance, adding violations as they are discovered during validation, and
 * throwing the exception only if violations were found. This allows validation logic to be written in a straightforward
 * manner while still collecting all errors.
 * </p>
 */
public class DataViolationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Locale string for internationalized error message generation.
   * 
   * <p>
   * Stores the user's locale preference for proper localization of error messages. When present, error handlers can use
   * this locale to generate appropriate translated messages. When null, the system default locale or current request
   * locale is typically used.
   * </p>
   */
  private String localeStr;

  /**
   * List of accumulated data validation violations.
   * 
   * <p>
   * Contains all validation violations that have been collected during the validation process. Each violation includes
   * field information, message keys, and parameters needed for generating localized error messages to display to users.
   * </p>
   */
  List<DataViolation> dataViolation = new ArrayList<>();

  public DataViolationException() {
  }

  /**
   * Creates a data violation exception with an initial violation and locale.
   * 
   * <p>
   * Convenience constructor for cases where a single violation needs to be reported immediately with specific locale
   * information. The violation is automatically added to the internal collection with field name translation enabled by
   * default.
   * </p>
   * 
   * @param field      the name of the field that caused the violation
   * @param messageKey the resource bundle key for the error message template
   * @param data       array of parameters for message template substitution
   * @param localeStr  the locale string for error message localization
   */
  public DataViolationException(final String field, final String messageKey, final Object[] data, String localeStr) {
    this.addDataViolation(field, messageKey, data);
    this.localeStr = localeStr;
  }

  /**
   * Creates a data violation exception with a single parameter violation and locale.
   * 
   * <p>
   * Convenience constructor for simple validation violations that require only one parameter for message formatting.
   * The single data value is automatically wrapped in an array for consistent internal handling.
   * </p>
   * 
   * @param field      the name of the field that caused the violation
   * @param messageKey the resource bundle key for the error message template
   * @param data       single parameter for message template substitution
   * @param localeStr  the locale string for error message localization
   */
  public DataViolationException(final String field, final String messageKey, final Object data, String localeStr) {
    this(field, messageKey, new Object[] { data }, localeStr);
  }

  /**
   * Creates a data violation exception with an initial violation without locale.
   * 
   * <p>
   * Convenience constructor for cases where locale information is not available at exception creation time. The locale
   * will be determined by error handlers based on the current request context or system defaults.
   * </p>
   * 
   * @param field      the name of the field that caused the violation
   * @param messageKey the resource bundle key for the error message template
   * @param data       array of parameters for message template substitution
   */
  public DataViolationException(final String field, final String messageKey, final Object[] data) {
    this.addDataViolation(field, messageKey, data);
  }

  /**
   * Adds a data violation with single parameter and translation control.
   * 
   * <p>
   * Adds a new validation violation to the collection with explicit control over field name translation. This method is
   * useful when you need to specify whether the field name should be translated for user display or used as-is for
   * technical field identifiers.
   * </p>
   * 
   * @param field              the name of the field that caused the violation
   * @param messageKey         the resource bundle key for the error message template
   * @param data               single parameter for message template substitution
   * @param translateFieldName true if the field name should be translated for display
   */
  public void addDataViolation(final String field, final String messageKey, final Object data,
      boolean translateFieldName) {
    dataViolation.add(new DataViolation(field, messageKey, new Object[] { data }, translateFieldName));
  }

  /**
   * Adds a data violation with single parameter and default field name translation.
   * 
   * <p>
   * Convenience method for adding violations with a single parameter where field name translation is desired. This is
   * the most common case for user-facing validation errors where field names should be translated to user-friendly
   * labels.
   * </p>
   * 
   * @param field      the name of the field that caused the violation
   * @param messageKey the resource bundle key for the error message template
   * @param data       single parameter for message template substitution
   */
  public void addDataViolation(final String field, final String messageKey, final Object data) {
    dataViolation.add(new DataViolation(field, messageKey, new Object[] { data }, true));
  }

  /**
   * Adds a data violation with multiple parameters and translation control.
   * 
   * <p>
   * Adds a validation violation with full parameter specification and explicit control over field name translation.
   * This method provides maximum flexibility for describing validation errors with multiple data parameters.
   * </p>
   * 
   * @param field              the name of the field that caused the violation
   * @param messageKey         the resource bundle key for the error message template
   * @param data               array of parameters for message template substitution
   * @param translateFieldName true if the field name should be translated for display
   */
  public void addDataViolation(final String field, final String messageKey, final Object[] data,
      boolean translateFieldName) {
    dataViolation.add(new DataViolation(field, messageKey, data, translateFieldName));
  }

  /**
   * Adds a data violation with multiple parameters and default field name translation.
   * 
   * <p>
   * Convenience method for adding violations with multiple parameters where field name translation is desired. This
   * method is commonly used for validation errors that require multiple context values for meaningful error message
   * generation.
   * </p>
   * 
   * @param field      the name of the field that caused the violation
   * @param messageKey the resource bundle key for the error message template
   * @param data       array of parameters for message template substitution
   */
  public void addDataViolation(final String field, final String messageKey, final Object[] data) {
    dataViolation.add(new DataViolation(field, messageKey, data, true));
  }

  public String getLocaleStr() {
    return localeStr;
  }

  public boolean hasErrors() {
    return this.dataViolation.size() > 0;
  }

  public List<DataViolation> getDataViolation() {
    return dataViolation;
  }

  @Override
  public String toString() {
    return "DataViolationException [localeStr=" + localeStr + ", dataViolation=" + dataViolation + "]";
  }

}
