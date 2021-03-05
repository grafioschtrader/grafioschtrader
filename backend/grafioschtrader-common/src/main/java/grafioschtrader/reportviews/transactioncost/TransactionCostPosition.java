package grafioschtrader.reportviews.transactioncost;

import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.SecurityCostPosition;

public class TransactionCostPosition extends SecurityCostPosition {
  public Transaction transaction;
  public double basePriceForTransactionCostMC;

  public TransactionCostPosition(Transaction transaction) {
    super();
    this.transaction = transaction;
  }

}
