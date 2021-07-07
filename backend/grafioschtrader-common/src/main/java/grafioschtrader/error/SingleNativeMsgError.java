package grafioschtrader.error;

/**
 * Most single message error should transformed in this class for a response.
 *
 * @author Hugo Graf
 *
 */
public class SingleNativeMsgError {
  public String message;

  public SingleNativeMsgError(String message) {
    this.message = message;
  }

  public SingleNativeMsgError(String message, boolean onlyFirstLine) {
    this.message = message.split("\r\n|\r|\n")[0];
  }
}
