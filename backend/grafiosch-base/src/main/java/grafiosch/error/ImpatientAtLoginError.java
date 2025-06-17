package grafiosch.error;

/**
 * The user has been locked out of the system and the proposal to remove this lock has not yet been processed by the
 * administrator. In this case, they will receive this message when they try to log in again.
 */
public class ImpatientAtLoginError extends SecurityBreachError {

  public ImpatientAtLoginError(String message) {
    super(message);
  }
}
