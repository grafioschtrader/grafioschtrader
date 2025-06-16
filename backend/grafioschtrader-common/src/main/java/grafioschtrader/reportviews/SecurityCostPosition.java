package grafioschtrader.reportviews;

import grafiosch.common.DataHelper;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Base cost position tracking transaction costs and tax costs for security transactions with proper currency precision handling")
public class SecurityCostPosition {

  @Schema(description = "Transaction costs (brokerage fees, commissions, etc.) converted to main currency")
  public double transactionCostMC;
  
  @Schema(description = "Tax costs (trading taxes, regulatory fees, etc.) converted to main currency")
  public double taxCostMC;

  protected int precisionMC;

  public SecurityCostPosition(int precisionMC) {
    this.precisionMC = precisionMC;
  }

  public double getTransactionCostMC() {
    return DataHelper.round(transactionCostMC, precisionMC);
  }

  public double getTaxCostMC() {
    return DataHelper.round(taxCostMC, precisionMC);
  }

}
