package grafioschtrader.reportviews.securityaccount;

import java.util.ArrayList;
import java.util.List;

import grafioschtrader.common.DataHelper;

public class SecurityPositionGrandSummary {

  /**
   * The main currency
   */
  public String currency;

  public double grandAccountValueSecurityMC = 0.0;

  public double grandSecurityRiskMC;

  /**
   * Grand total of Gain/Loss
   */
  public double grandGainLossSecurityMC = 0.0;

  public double grandTaxCostMC = 0.0;

  public double grandCurrencyGainLossMC = 0.0;

  protected int precision;

  // public double grandTransactionCostMC = 0.0;

  public List<SecurityPositionGroupSummary> securityPositionGroupSummaryList = new ArrayList<>();

  public SecurityPositionGrandSummary(String currency, Integer precision) {
    super();
    this.currency = currency;
    this.precision = precision;
  }

  /**
   * Add the group total to the grand total, it should be called for each group.
   *
   * @param securityPositionGroupSummary
   */
  public void calcGrandTotal(SecurityPositionGroupSummary securityPositionGroupSummary) {
    securityPositionGroupSummaryList.add(securityPositionGroupSummary);

    grandAccountValueSecurityMC += securityPositionGroupSummary.groupAccountValueSecurityMC;
    grandGainLossSecurityMC += securityPositionGroupSummary.groupGainLossSecurityMC;
    grandSecurityRiskMC += securityPositionGroupSummary.groupSecurityRiskMC;
    grandCurrencyGainLossMC += securityPositionGroupSummary.groupCurrencyGainLossMC;
    // grandTaxCostMC += securityPositionGroupSummary.groupTaxCost;
    // grandTransactionCostMC +=
    // securityPositionGroupSummary.groupTransactionCostMC;

  }

  public void roundGrandTotals() {
    grandAccountValueSecurityMC = DataHelper.round(grandAccountValueSecurityMC);
    grandGainLossSecurityMC = DataHelper.round(grandGainLossSecurityMC);
    grandTaxCostMC = DataHelper.round(grandTaxCostMC);
    grandSecurityRiskMC = DataHelper.roundStandard(grandSecurityRiskMC);
    // grandTransactionCostMC = DataHelper.round(grandTransactionCostMC);
  }

  public double getGrandAccountValueSecurityMC() {
    return DataHelper.round(grandAccountValueSecurityMC, precision);
  }

  public double getGrandSecurityRiskMC() {
    return DataHelper.round(grandSecurityRiskMC, precision);
  }

  public double getGrandGainLossSecurityMC() {
    return DataHelper.round(grandGainLossSecurityMC, precision);
  }

  public double getGrandTaxCostMC() {
    return DataHelper.round(grandTaxCostMC, precision);
  }

  public double getGrandCurrencyGainLossMC() {
    return DataHelper.round(grandCurrencyGainLossMC, precision);
  }

}
