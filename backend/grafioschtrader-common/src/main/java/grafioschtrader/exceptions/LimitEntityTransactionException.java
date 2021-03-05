package grafioschtrader.exceptions;

import grafioschtrader.error.LimitEntityTransactionError;

public class LimitEntityTransactionException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  public final LimitEntityTransactionError limitEntityTransactionError;

  public LimitEntityTransactionException(LimitEntityTransactionError limitEntityTransactionError) {
    this.limitEntityTransactionError = limitEntityTransactionError;
  }
}
