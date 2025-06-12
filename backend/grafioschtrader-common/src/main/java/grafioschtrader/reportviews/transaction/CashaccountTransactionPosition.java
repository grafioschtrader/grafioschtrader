package grafioschtrader.reportviews.transaction;

import grafiosch.common.DataHelper;
import grafioschtrader.entities.Transaction;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        Represents a transaction along with the running account balance after that transaction. 
        Used for displaying transaction history with cumulative balance calculations.""")
public class CashaccountTransactionPosition {
  @Schema(description = "The transaction details including amount, type, and timestamp")
  public Transaction transaction;
  @Schema(description = "The running account balance after this transaction was processed")
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
