package grafiosch.error;

/**
 * Security breach error that requires immediate user logout and session termination.
 * 
 * <p>This specialized security error indicates severe security violations that
 * compromise user session integrity and require immediate logout to protect
 * both the user account and system security. It triggers client-side logout
 * procedures and session cleanup.</p>
 * 
 */
public class ErrorWithLogout extends SecurityBreachError {

  /**
   * Creates a security error that triggers immediate logout with the specified message.
   * 
   * @param message description of the security violation requiring immediate logout
   */
  public ErrorWithLogout(String message) {
    super(message);
  }

}
