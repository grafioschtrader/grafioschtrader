package grafiosch.error;

/**
 * Error response for entity transaction limit violations.
 * 
 * <p>
 * This class represents errors that occur when users exceed configured transaction limits for specific entity types. It
 * provides detailed information about the limit violation including the entity type, configured limit, and current
 * transaction count for proper error reporting and user guidance. In the frontend, the user could be shown a dialog in
 * which he could suggest a higher limit for this entity with regard to CUD operations.
 * </p>
 */
public class LimitEntityTransactionError {
  public final String entity;
  public final int limit;
  public final int transactionsCount;

  /**
   * Creates a transaction limit error with entity details and violation data.
   * 
   * <p>
   * Constructs a comprehensive limit violation error that includes all necessary information for error reporting, user
   * notification, and system monitoring of transaction limit enforcement.
   * </p>
   * 
   * @param entity            the entity type that exceeded its transaction limit
   * @param limit             the configured maximum transaction limit for this entity
   * @param transactionsCount the current transaction count that violated the limit
   */
  public LimitEntityTransactionError(String entity, int limit, int transactionsCount) {
    this.entity = entity;
    this.limit = limit;
    this.transactionsCount = transactionsCount;
  }
}
