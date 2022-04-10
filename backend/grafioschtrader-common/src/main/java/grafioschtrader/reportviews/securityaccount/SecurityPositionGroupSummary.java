package grafioschtrader.reportviews.securityaccount;

import java.util.ArrayList;
import java.util.List;

import grafioschtrader.common.DataHelper;

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
  public double groupCurrencyGainLossMC = 0.0;

  public double groupValueSecurityShort;
  public double groupSecurityRiskMC;

  public List<SecurityPositionSummary> securityPositionSummaryList = new ArrayList<>();

  protected int precision;
  private int precisionMC;

  public SecurityPositionGroupSummary(int precision) {
    this.precision = precision;
  }

  public void addToGroupSummaryAndCalcGroupTotals(SecurityPositionSummary securityPositionSummary) {
    precision = securityPositionSummary.precision;
    precisionMC = securityPositionSummary.precisionMC;
    securityPositionSummaryList.add(securityPositionSummary);
    groupGainLossSecurityMC += securityPositionSummary.gainLossSecurityMC;
    groupCurrencyGainLossMC += securityPositionSummary.currencyGainLossMC;
    groupAccountValueSecurityMC += securityPositionSummary.accountValueSecurityMC;

    groupValueSecurityShort += securityPositionSummary.valueSecurity
        * securityPositionSummary.securitycurrency.getLeverageFactor();
    securityPositionSummary.securityRiskMC = securityPositionSummary.valueSecurityMC
        * securityPositionSummary.securitycurrency.getLeverageFactor();
    
    groupSecurityRiskMC += securityPositionSummary.securityRiskMC;

  }

  public double getGroupAccountValueSecurityMC() {
    return DataHelper.round(groupAccountValueSecurityMC, precisionMC);
  }

  public double getGroupGainLossSecurityMC() {
    return DataHelper.round(groupGainLossSecurityMC, precisionMC);
  }

  public double getGroupValueSecurityShort() {
    return DataHelper.round(groupValueSecurityShort, precision);
  }

  public double getGroupSecurityRiskMC() {
    return DataHelper.round(groupSecurityRiskMC, precisionMC);
  }

  public double getGroupCurrencyGainLossMC() {
    return DataHelper.round(groupCurrencyGainLossMC, precisionMC);
  }

}
