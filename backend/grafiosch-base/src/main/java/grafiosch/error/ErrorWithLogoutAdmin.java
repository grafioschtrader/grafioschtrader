package grafiosch.error;

/**
 * Security breach error for admin users that enables self-release from lockout.
 *
 * <p>When an admin exceeds security breach or request limit thresholds, they cannot
 * approve their own release request through the normal ProposeUserTask flow.
 * This error signals the frontend to show a self-release confirmation dialog
 * instead of the regular "write a note to admin" dialog.</p>
 */
public class ErrorWithLogoutAdmin extends SecurityBreachError {

  /**
   * Creates a security error that triggers admin self-release dialog with the specified message.
   *
   * @param message description of the security violation requiring admin self-release
   */
  public ErrorWithLogoutAdmin(String message) {
    super(message);
  }

}
