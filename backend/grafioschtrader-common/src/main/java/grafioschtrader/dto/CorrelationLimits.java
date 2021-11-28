package grafioschtrader.dto;

import grafioschtrader.GlobalConstants;

public class CorrelationLimits {
  public final TenantLimit tenantLimit;
  public final String dailyConfiguration = GlobalConstants.CORR_DAILY;
  public final String monthlyConfiguration = GlobalConstants.CORR_MONTHLY;
  public final String annualConfiguration = GlobalConstants.CORR_ANNUAL;
  public final byte requiredMinPeriods =  GlobalConstants.REQUIRED_MIN_PERIODS;

  public CorrelationLimits(TenantLimit tenantLimit) {
    super();
    this.tenantLimit = tenantLimit;
  }
}
