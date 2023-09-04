package grafioschtrader.reportviews.transaction;

import grafioschtrader.common.DataHelper;
import grafioschtrader.entities.Transaction;

/**
 * It is a transaction position with the balance of the account.
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
  
  public void roundBalance(int precision) {
    balance = DataHelper.round(balance, precision); 
  }

}
