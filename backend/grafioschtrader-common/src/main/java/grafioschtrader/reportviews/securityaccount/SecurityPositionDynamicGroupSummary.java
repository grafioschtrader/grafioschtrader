package grafioschtrader.reportviews.securityaccount;

public class SecurityPositionDynamicGroupSummary<T> extends SecurityPositionGroupSummary {

  public T groupField;

  public SecurityPositionDynamicGroupSummary(T groupField) {
    super();
    this.groupField = groupField;
  }

}
