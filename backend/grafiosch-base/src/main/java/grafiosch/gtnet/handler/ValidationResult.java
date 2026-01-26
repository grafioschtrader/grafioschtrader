package grafiosch.gtnet.handler;

/**
 * Result of message validation in handlers.
 *
 * Provides a simple pass/fail result with optional error details for failed validations.
 */
public record ValidationResult(boolean valid, String errorCode, String message) {

  /**
   * Creates a successful validation result.
   */
  public static ValidationResult ok() {
    return new ValidationResult(true, null, null);
  }

  /**
   * Creates a failed validation result with error details.
   *
   * @param errorCode i18n error code for translation
   * @param message   human-readable error description
   */
  public static ValidationResult invalid(String errorCode, String message) {
    return new ValidationResult(false, errorCode, message);
  }
}
