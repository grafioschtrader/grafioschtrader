package grafioschtrader.exceptions;

import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;

/** A transaction budget refusal that stops a replay while retaining its previously committed bookings. */
public class TransactionLimitExceededException extends GeneralNotTranslatedWithArgumentsException {
  private static final long serialVersionUID = 1L;

  /** @param limit the resolved maximum number of transactions for the tenant */
  public TransactionLimitExceededException(int limit) {
    super("gt.transaction.limit.exceeded", new Object[] { limit });
  }
}
