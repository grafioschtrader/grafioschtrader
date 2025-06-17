package grafiosch.error;

/**
 * Error response for security violations involving tenant ID misuse.
 * 
 * <p>
 * This class represents security breach responses when users attempt to access or manipulate tenant data they are not
 * authorized to access. It provides a standardized error structure for tenant-based security violations in multi-tenant
 * applications.
 * </p>
 * 
 * <h3>Common Violations:</h3>
 * <p>
 * Triggered by attempts to manipulate tenant IDs in requests, URL tampering, or cross-tenant data access attempts in
 * multi-tenant environments.
 * </p>
 */
public class SecurityBreachError {
  public String message;

  /**
   * Creates a security breach error with the specified violation message.
   * 
   * <p>
   * Used to report tenant ID misuse and other security violations with descriptive messages for proper security
   * incident tracking and user notification.
   * </p>
   * 
   * @param message description of the security breach or violation
   */
  public SecurityBreachError(String message) {
    this.message = message;
  }
}
