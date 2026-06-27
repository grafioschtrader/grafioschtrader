package grafioschtrader.reportviews.securityaccount;

import grafiosch.BaseConstants;

public class SecurityPositionDynamicGroupSummary<T> extends SecurityPositionGroupSummary {

  public T groupField;

  public SecurityPositionDynamicGroupSummary(T groupField) {
    // Placeholder precision only: overwritten with the currency-aware precision of the first
    // position in SecurityPositionGroupSummary.addToGroupSummaryAndCalcGroupTotals(). A group is
    // always populated with at least one position before any rounding getter is read, so this
    // seed value is never used for display.
    super(BaseConstants.FID_STANDARD_FRACTION_DIGITS);
    this.groupField = groupField;
  }

}
