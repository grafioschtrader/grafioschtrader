package grafioschtrader.udffill.ai;

/**
 * The daily request limit a tenant set for one of its keys is reached. Nothing was reserved, so neither the limit of
 * the user nor the log counts the attempt; the caller moves on to the next provider of the tenant.
 */
public class AITenantLimitException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * @param limit the daily request limit of the key
   */
  public AITenantLimitException(int limit) {
    super("daily request limit of the tenant (" + limit + ") reached");
  }
}
