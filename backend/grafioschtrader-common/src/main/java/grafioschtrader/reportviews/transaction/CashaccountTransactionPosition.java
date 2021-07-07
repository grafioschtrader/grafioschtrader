package grafioschtrader.reportviews.transaction;

import grafioschtrader.entities.Transaction;

/**
 * It is a transaction position with the balance of the account.
 *
 * @author Hugo Graf
 *
 */
public class CashaccountTransactionPosition {
  public Transaction transaction;
  public double balance;

  public CashaccountTransactionPosition(Transaction transaction, double balance) {
    super();
    this.transaction = transaction;
    this.balance = balance;
  }

}
