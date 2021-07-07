package grafioschtrader.error;

/**
 * Error which supports a field name with message.
 *
 * @author Hugo Graf
 *
 */
public class FieldError {

  private final String field;

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
