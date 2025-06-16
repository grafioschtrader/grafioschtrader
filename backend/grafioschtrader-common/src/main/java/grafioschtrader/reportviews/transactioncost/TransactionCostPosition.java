package grafioschtrader.reportviews.transactioncost;

import grafiosch.common.DataHelper;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.SecurityCostPosition;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Detailed cost position for a specific security transaction, including transaction context and basis price impact from costs and taxes")
public class TransactionCostPosition extends SecurityCostPosition {

  @Schema(description = "The specific transaction that incurred these costs, containing all trade details and context")
  public Transaction transaction;
  
  @Schema(description = "Base price calculation (units * quotation + accrued interest), expressed in main currency")
  public double basePriceForTransactionCostMC;

  public TransactionCostPosition(Transaction transaction, int precisionMC) {
    super(precisionMC);
    this.transaction = transaction;
  }

  public double getBasePriceForTransactionCostMC() {
    return DataHelper.round(basePriceForTransactionCostMC, precisionMC);
  }

}
