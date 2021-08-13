package grafioschtrader.reportviews.securityaccount;

import grafioschtrader.GlobalConstants;

public class SecurityPositionDynamicGroupSummary<T> extends SecurityPositionGroupSummary {

  public T groupField;

  public SecurityPositionDynamicGroupSummary(T groupField) {
    // TODO GlobalConstants.FID_STANDARD_FRACTION_DIGITS maybe wrong
    super(GlobalConstants.FID_STANDARD_FRACTION_DIGITS);
    this.groupField = groupField;
  }

}
