package grafiosch.error;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for multiple field validation errors in form submissions.
 * 
 * <p>
 * This class aggregates multiple field-specific validation errors for comprehensive form validation responses. It
 * provides a structured way to collect and return all validation failures for a single request, enabling clients to
 * display all field errors simultaneously rather than one at a time.
 * </p>
 */
public class ValidationError {

  /** List of individual field validation errors. */
  private final List<FieldError> fieldErrors = new ArrayList<>();

  public ValidationError() {
  }

  /**
   * Adds a field validation error to the collection.
   * 
   * <p>
   * Creates and adds a new field error with the specified path and message to the validation error collection. This
   * allows incremental building of comprehensive validation error responses during form processing.
   * </p>
   * 
   * <h3>Error Accumulation:</h3>
   * <p>
   * Multiple errors can be added for different fields, and even multiple errors for the same field if needed, providing
   * complete validation feedback for complex form validation scenarios.
   * </p>
   * 
   * @param path    the field path or identifier that failed validation
   * @param message the error message describing the validation failure
   */
  public void addFieldError(final String path, final String message) {
    final FieldError error = new FieldError(path, message);
    fieldErrors.add(error);
  }

  public List<FieldError> getFieldErrors() {
    return fieldErrors;
  }
}
