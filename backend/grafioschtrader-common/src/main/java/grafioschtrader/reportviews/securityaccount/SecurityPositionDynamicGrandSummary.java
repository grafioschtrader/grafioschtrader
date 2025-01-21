package grafioschtrader.reportviews.securityaccount;

import grafiosch.common.DataHelper;
import grafioschtrader.common.DataBusinessHelper;

public class SecurityPositionDynamicGrandSummary<S extends SecurityPositionGroupSummary>
    extends SecurityPositionGrandSummary {

  public double grandValueSecurityShort;
  public double grandSecurityRiskMC;

  public SecurityPositionDynamicGrandSummary(String currency, int precision) {
    super(currency, precision);
  }

  public void calcGrandTotal(SecurityPositionDynamicGroupSummary<S> securityPositionGroupSummary) {
    super.calcGrandTotal(securityPositionGroupSummary);
    grandValueSecurityShort += securityPositionGroupSummary.groupValueSecurityShort;
    grandSecurityRiskMC += securityPositionGroupSummary.groupSecurityRiskMC;
  }

  @Override
  public void roundGrandTotals() {
    super.roundGrandTotals();
    grandValueSecurityShort = DataBusinessHelper.round(grandValueSecurityShort);
    grandSecurityRiskMC = DataBusinessHelper.roundStandard(grandSecurityRiskMC);
  }

  public double getGrandValueSecurityShort() {
    return DataHelper.round(grandValueSecurityShort, precision);
  }

  @Override
  public double getGrandSecurityRiskMC() {
    return DataHelper.round(grandSecurityRiskMC, precision);
  }

}
