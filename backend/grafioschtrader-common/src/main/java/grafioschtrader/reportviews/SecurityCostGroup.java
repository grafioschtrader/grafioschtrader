package grafioschtrader.reportviews;

import grafiosch.common.DataHelper;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Base class for aggregating security transaction costs with totals, averages, and statistical analysis within a grouping context")
public class SecurityCostGroup {
  @Schema(description = "Total transaction costs (brokerage fees, commissions) for all transactions in this group")
  public double groupTotalTransactionCostMC;

  @Schema(description = "Total tax costs (trading taxes, regulatory fees) for all transactions in this group")
  public double groupTotalTaxCostMC;

  @Schema(description = "Number of transactions that incurred costs within this group")
  public double groupTotalAverageTransactionCostMC;

  @Schema(description = "Number of paid transactions that incurred costs within this group")
  public int groupCountPaidTransaction;

  protected int precisionMC;

  public SecurityCostGroup(int precisionMC) {
    this.precisionMC = precisionMC;
  }

  /**
   * Adds the costs from an individual security cost position to the group totals. Accumulates both transaction costs
   * and tax costs for comprehensive cost tracking.
   * 
   * @param securityCostPosition the individual cost position to add to group totals
   */
  public void sumPositionToGroupTotal(SecurityCostPosition securityCostPosition) {
    groupTotalTransactionCostMC += securityCostPosition.transactionCostMC;
    groupTotalTaxCostMC += securityCostPosition.taxCostMC;
  }

  /**
   * Calculates average costs based on the number of cost-bearing transactions. Computes meaningful per-transaction
   * averages to enable cost efficiency analysis.
   * 
   * @param size the number of transactions that contributed to the group totals
   */
  public void calcAverages(int size) {
    groupCountPaidTransaction = size;

    if (groupCountPaidTransaction > 0) {
      groupTotalAverageTransactionCostMC = groupTotalTransactionCostMC / groupCountPaidTransaction;
    }
  }

  /**
   * Calculates the aggregated totals and averages for all transaction cost positions within this security account
   * group. Processes each individual position to build comprehensive group statistics including total costs, average
   * costs per transaction, and cost efficiency metrics.
   * 
   * <p>
   * This method iterates through all transaction cost positions, summing the costs and calculating meaningful averages
   * that help users understand the cost characteristics of their trading activity with this particular security
   * account.
   * </p>
   */
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
