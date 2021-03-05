package grafioschtrader.reportviews;

public class SecurityCostGroup {
  public double groupTotalTransactionCostMC;
  public double groupTotalTaxCostMc;
  public double groupTotalAverageTransactionCostMC;
  public int groupCountPaidTransaction;

  public void sumPositionToGroupTotal(SecurityCostPosition securityCostPosition) {
    groupTotalTransactionCostMC += securityCostPosition.transactionCostMC;
    groupTotalTaxCostMc += securityCostPosition.taxCostMC;
  }

  public void calcAverages(int size) {
    groupCountPaidTransaction = size;

    if (groupCountPaidTransaction > 0) {
      groupTotalAverageTransactionCostMC = groupTotalTransactionCostMC / groupCountPaidTransaction;
    }
  }

  public void caclulateGroupSummary() {
  };

}
