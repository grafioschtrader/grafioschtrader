package grafioschtrader.reportviews.securityaccount;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for a group of securities. For example they may be grouped by
 * currency or asset class.
 * 
 * @author Hugo Graf
 *
 */
public abstract class SecurityPositionGroupSummary {

  public double groupAccountValueSecurityMC;
  public double groupGainLossSecurityMC = 0.0;

  public double groupValueSecurityShort;
  public double groupSecurityRiskMC;

  public List<SecurityPositionSummary> securityPositionSummaryList = new ArrayList<>();

  public void addToGroupSummaryAndCalcGroupTotals(SecurityPositionSummary securityPositionSummary) {
    securityPositionSummaryList.add(securityPositionSummary);
    groupGainLossSecurityMC += securityPositionSummary.gainLossSecurityMC;
    groupAccountValueSecurityMC += securityPositionSummary.accountValueSecurityMC;

    groupValueSecurityShort += securityPositionSummary.valueSecurity
        * (securityPositionSummary.securitycurrency.isShortSecurity() ? -1 : 1);
    groupSecurityRiskMC += securityPositionSummary.valueSecurityMC
        * (securityPositionSummary.securitycurrency.isShortSecurity() ? -1 : 1);

  }

}
