package grafioschtrader.reportviews;

import grafioschtrader.common.DataHelper;

public class SecurityCostGroup {
  public double groupTotalTransactionCostMC;
  public double groupTotalTaxCostMC;
  public double groupTotalAverageTransactionCostMC;
  public int groupCountPaidTransaction;

  protected int precisionMC;

  public SecurityCostGroup(int precisionMC) {
    this.precisionMC = precisionMC;
  }

  public void sumPositionToGroupTotal(SecurityCostPosition securityCostPosition) {
    groupTotalTransactionCostMC += securityCostPosition.transactionCostMC;
    groupTotalTaxCostMC += securityCostPosition.taxCostMC;
  }

  public void calcAverages(int size) {
    groupCountPaidTransaction = size;

    if (groupCountPaidTransaction > 0) {
      groupTotalAverageTransactionCostMC = groupTotalTransactionCostMC / groupCountPaidTransaction;
    }
  }

  public void caclulateGroupSummary() {
  }

  public double getGroupTotalTaxCostMC() {
    return DataHelper.round(groupTotalTaxCostMC, precisionMC);
  }

  public double getGroupTotalTransactionCostMC() {
    return DataHelper.round(groupTotalTransactionCostMC, precisionMC);
  }

  public double getGroupTotalTaxCostMc() {
    return DataHelper.round(groupTotalTaxCostMC, precisionMC);
  }

  public double getGroupTotalAverageTransactionCostMC() {
    return DataHelper.round(groupTotalAverageTransactionCostMC, precisionMC);
  }

}
