package grafiosch.exceptions;

import grafiosch.error.LimitEntityTransactionError;

/**
 * Runtime exception thrown when entity transaction limits are exceeded.
 * 
 * <p>This exception is raised when users attempt to perform more transactions
 * on a specific entity type than allowed by the configured limits. It carries
 * detailed error information about the limit violation for proper exception
 * handling and user notification.</p>
 * 
 */
public class LimitEntityTransactionException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  public final LimitEntityTransactionError limitEntityTransactionError;

  /**
   * Creates a transaction limit exception with detailed violation information.
   * 
   * <p>Constructs the exception with comprehensive error data that can be
   * used by exception handlers to provide meaningful error responses and
   * appropriate user guidance about transaction limit violations.</p>
   * 
   * @param limitEntityTransactionError detailed information about the limit violation
   */
  public LimitEntityTransactionException(LimitEntityTransactionError limitEntityTransactionError) {
    this.limitEntityTransactionError = limitEntityTransactionError;
  }
}
