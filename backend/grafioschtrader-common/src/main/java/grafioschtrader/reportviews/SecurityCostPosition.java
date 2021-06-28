package grafioschtrader.reportviews;

import grafioschtrader.common.DataHelper;

public class SecurityCostPosition {

  public double transactionCostMC;
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
