package grafiosch.exceptions;

import java.util.List;

/**
 * Checked exception for background task execution failures with transaction control.
 * 
 * <p>
 * This exception is thrown when background tasks encounter errors during execution. It provides comprehensive error
 * information including localized message keys, system error details, and transaction rollback control for proper error
 * handling and recovery in asynchronous task processing.
 * </p>
 */
public class TaskBackgroundException extends Exception {
  private static final long serialVersionUID = 1L;

  /** Localization key for user-facing error messages. */
  private String errorMessagesKey;
  /** List of technical system error messages for debugging. */
  private List<String> errorMsgOfSystem;
  /** Flag indicating whether transaction rollback should be performed. */
  private boolean rollback = true;

  /**
   * Creates a background task exception with message key and rollback control.
   * 
   * <p>
   * Used for background task failures where localized error messaging is needed along with specific transaction
   * rollback behavior. Suitable for business logic errors that don't require detailed system error information.
   * </p>
   * 
   * @param errorMessagesKey localization key for user-facing error messages
   * @param rollback         whether transaction rollback should be performed
   */
  public TaskBackgroundException(String errorMessagesKey, boolean rollback) {
    super();
    this.errorMessagesKey = errorMessagesKey;
    this.rollback = rollback;
  }

  /**
   * Creates a background task exception with complete error information and transaction control.
   * 
   * <p>
   * Used for comprehensive error reporting in background tasks, including both user-facing localized messages and
   * detailed system error information for debugging. Provides full control over transaction rollback behavior.
   * </p>
   * 
   * @param errorMessagesKey localization key for user-facing error messages
   * @param errorMsgOfSystem list of technical system error messages for debugging
   * @param rollback         whether transaction rollback should be performed
   */
  public TaskBackgroundException(String errorMessagesKey, List<String> errorMsgOfSystem, boolean rollback) {
    this(errorMessagesKey, rollback);
    this.rollback = rollback;
  }

  public String getErrorMessagesKey() {
    return errorMessagesKey;
  }

  public List<String> getErrorMsgOfSystem() {
    return errorMsgOfSystem;
  }

  public boolean isRollback() {
    return rollback;
  }

}
