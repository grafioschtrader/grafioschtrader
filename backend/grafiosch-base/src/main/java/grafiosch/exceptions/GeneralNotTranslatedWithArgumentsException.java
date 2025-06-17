package grafiosch.exceptions;

/**
 * Runtime exception for deferred message translation when user locale is not accessible.
 * 
 * <p>
 * This exception is used in scenarios where an error message needs to be generated but the user's locale information is
 * not available at the time of exception creation. Instead of using a default locale or hardcoded message, the
 * exception stores the message key and parameters, allowing the translation to be performed later when the user's
 * locale can be determined.
 * </p>
 * 
 * <h3>Deferred Translation Pattern:</h3>
 * <p>
 * The exception implements a deferred translation pattern where:
 * </p>
 * <ol>
 * <li>Exception is created with message key and parameters</li>
 * <li>Exception is thrown and propagated up the call stack</li>
 * <li>Exception handler with access to user context performs translation</li>
 * <li>Localized message is generated using the user's preferred locale</li>
 * </ol>
 * 
 * <h3>Use Cases:</h3>
 * <ul>
 * <li><strong>Background Processing:</strong> Errors in scheduled tasks or background threads where user context is not
 * available</li>
 * <li><strong>Service Layer Exceptions:</strong> Business logic errors where user locale information has not been
 * propagated down the call stack</li>
 * <li><strong>Security Context Issues:</strong> Situations where security context is not properly established but
 * errors need user-friendly messages</li>
 * <li><strong>Multi-tenant Scenarios:</strong> Cross-tenant operations where the target user's locale differs from the
 * current user's locale</li>
 * </ul>
 * 
 * <h3>Message Resolution:</h3>
 * <p>
 * The stored message key and arguments are processed by exception handlers that have access to the user's security
 * context and locale preferences. This ensures that error messages are properly localized when they reach the
 * presentation layer.
 * </p>
 * 
 * <h3>Integration:</h3>
 * <p>
 * This exception is typically caught by REST error handlers that can access the current user's locale from the security
 * context and use Spring's MessageSource to resolve the final localized message.
 * </p>
 */
public class GeneralNotTranslatedWithArgumentsException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  /**
   * The message key for internationalized error message lookup.
   * 
   * <p>
   * References a key in the application's message resource bundles that contains the localized error message template.
   * This key will be used later by exception handlers to resolve the appropriate translated message based on the user's
   * locale.
   * </p>
   */
  private final String messageKey;
  /**
   * Array of arguments for message template parameter substitution.
   * 
   * <p>
   * Contains the values that will be substituted into the message template placeholders during deferred message
   * translation. The arguments are preserved in their original form to ensure proper formatting when the message is
   * eventually resolved with the correct locale.
   * </p>
   */
  private final Object arguments[];

  /**
   * Creates an exception with message key and arguments for deferred translation.
   * 
   * <p>Initializes the exception with all information needed for later message
   * translation. The message key should correspond to an entry in the application's
   * message resource bundles, and the arguments should match the expected parameters
   * for that message template.</p>
   * 
   * <p><strong>Message Template Format:</strong></p>
   * <p>The message key should reference a template that uses standard message
   * formatting placeholders (e.g., {0}, {1}, etc.) that correspond to the
   * provided arguments array.</p>
   * 
   * <p><strong>Example Usage:</strong></p>
   * <pre>{@code
   * // Message template: "User {0} does not have permission for operation {1}"
   * throw new GeneralNotTranslatedWithArgumentsException(
   *     "error.user.permission.denied", 
   *     new Object[]{"john.doe", "DELETE_USER"}
   * );
   * }</pre>
   * 
   * @param messageKey the resource bundle key for the error message template
   * @param arguments array of parameters for message template substitution, may be null
   */
  public GeneralNotTranslatedWithArgumentsException(String messageKey, Object arguments[]) {
    this.messageKey = messageKey;
    this.arguments = arguments;
  }

  public String getMessageKey() {
    return messageKey;
  }

  public Object[] getArguments() {
    return arguments;
  }

}
