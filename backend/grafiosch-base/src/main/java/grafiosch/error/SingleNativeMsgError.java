package grafiosch.error;

/**
 * Simple error response container for single message REST API error responses.
 */
public class SingleNativeMsgError {
  public String message;

  /**
   * Creates a new single message error with the complete provided message.
   * 
   * <p>
   * This constructor preserves the entire message content, including any line breaks or formatting. It is suitable for
   * cases where the full message content should be displayed to the user, such as detailed business rule violations or
   * user-friendly error explanations.
   * </p>
   * 
   * <p>
   * <strong>Message Preservation:</strong>
   * </p>
   * <p>
   * The message is stored exactly as provided, maintaining all formatting, line breaks, and special characters. This
   * ensures that carefully crafted error messages retain their intended presentation and readability.
   * </p>
   * 
   * @param message the complete error message to be included in the response
   */
  public SingleNativeMsgError(String message) {
    this.message = message;
  }

  public SingleNativeMsgError(String message, boolean onlyFirstLine) {
    this.message = message.split("\r\n|\r|\n")[0];
  }
}
