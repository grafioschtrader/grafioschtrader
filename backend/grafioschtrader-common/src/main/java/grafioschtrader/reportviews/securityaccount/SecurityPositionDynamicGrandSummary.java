package grafioschtrader.reportviews.securityaccount;

import com.fasterxml.jackson.annotation.JsonInclude;

import grafiosch.common.DataHelper;
import grafioschtrader.common.DataBusinessHelper;
import io.swagger.v3.oas.annotations.media.Schema;

public class SecurityPositionDynamicGrandSummary<S extends SecurityPositionGroupSummary>
    extends SecurityPositionGrandSummary {

  public double grandValueSecurityShort;
  public double grandSecurityRiskMC;

  @Schema(description = """
      Holdings treemap of this report. Only set by the report "security asset classes with cash"; every other grouping
      leaves it null and does not serialize it.""")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public HoldingsTreemap holdingsTreemap;

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
