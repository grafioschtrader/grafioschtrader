package grafioschtrader.error;

public class LimitEntityTransactionError {
  public final String entity;
  public final int limit;
  public final int transactionsCount;

  public LimitEntityTransactionError(String entity, int limit, int transactionsCount) {
    this.entity = entity;
    this.limit = limit;
    this.transactionsCount = transactionsCount;
  }
}
