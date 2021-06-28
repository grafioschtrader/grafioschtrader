package grafioschtrader.reportviews.transactioncost;

import grafioschtrader.common.DataHelper;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.SecurityCostPosition;

public class TransactionCostPosition extends SecurityCostPosition {

  public Transaction transaction;
  public double basePriceForTransactionCostMC;

  public TransactionCostPosition(Transaction transaction, int precisionMC) {
    super(precisionMC);
    this.transaction = transaction;
  }

  public double getBasePriceForTransactionCostMC() {
    return DataHelper.round(basePriceForTransactionCostMC, precisionMC);
  }

}
