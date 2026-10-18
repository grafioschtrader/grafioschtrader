package grafioschtrader.udffill.http;

/**
 * A fetch of {@link UDFFillHttp} that was refused before or during the request, or that failed technically. A caller
 * treats both alike as a technical failure; the message says which check refused the URL.
 */
public class UDFFillHttpException extends Exception {

  private static final long serialVersionUID = 1L;

  /** True when a check refused the URL or the response, false for a transport failure. */
  private final boolean refused;

  public UDFFillHttpException(String message, boolean refused) {
    super(message);
    this.refused = refused;
  }

  public UDFFillHttpException(String message, Throwable cause) {
    super(message, cause);
    this.refused = false;
  }

  public boolean isRefused() {
    return refused;
  }
}
