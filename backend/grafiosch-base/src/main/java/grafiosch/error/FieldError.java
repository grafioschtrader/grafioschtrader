package grafiosch.error;

/**
 * Immutable error container for field-specific validation errors.
 * 
 * <p>This class represents a single field validation error with both the field
 * identifier and associated error message. It provides a standardized structure
 * for form validation errors in REST API responses, enabling clients to display
 * field-specific error messages in user interfaces.</p>
 * 
 */
public class FieldError {

  /** The field identifier associated with this validation error. */
  private final String field;

  /** The error message describing the validation failure. */
  private final String message;

  public FieldError(final String field, final String message) {
    this.field = field;
    this.message = message;
  }

  public String getField() {
    return field;
  }

  public String getMessage() {
    return message;
  }
}
