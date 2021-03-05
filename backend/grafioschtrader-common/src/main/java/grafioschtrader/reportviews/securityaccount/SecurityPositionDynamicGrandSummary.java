package grafioschtrader.reportviews.securityaccount;

import grafioschtrader.common.DataHelper;

public class SecurityPositionDynamicGrandSummary<S extends SecurityPositionGroupSummary>
    extends SecurityPositionGrandSummary {

  public double grandValueSecurityShort;
  public double grandSecurityRiskMC;

  public SecurityPositionDynamicGrandSummary(String currency) {
    super(currency);

  }

  public void calcGrandTotal(SecurityPositionDynamicGroupSummary<S> securityPositionGroupSummary) {
    super.calcGrandTotal(securityPositionGroupSummary);
    grandValueSecurityShort += securityPositionGroupSummary.groupValueSecurityShort;
    grandSecurityRiskMC += securityPositionGroupSummary.groupSecurityRiskMC;
  }

  @Override
  public void roundGrandTotals() {
    super.roundGrandTotals();
    grandValueSecurityShort = DataHelper.round(grandValueSecurityShort);
    grandSecurityRiskMC = DataHelper.round2(grandSecurityRiskMC);
  }

}
