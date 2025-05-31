package grafioschtrader.dto;

import grafiosch.dto.TenantLimit;
import grafioschtrader.GlobalConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    This is useful information for configuring the user interface.
    Provides configuration parameters and limits for correlation calculations. This includes tenant-specific limits,
    predefined configuration strings for different sampling periods (daily, monthly, annual) which detail default,
    minimum, and maximum rolling window sizes, and the minimum number of periods required for a correlation calculation.""")
public class CorrelationLimits {
  @Schema(description = "Tenant-specific limits applicable to correlation operations.It defines the maximum number of correlation sets.")
  public final TenantLimit tenantLimit;

  @Schema(description = """
      Configuration string for daily correlation calculations, formatted as 'default,min,max'.
      Represents default rolling window size, minimum window size, and maximum window size in days.""")
  public final String dailyConfiguration = GlobalConstants.CORR_DAILY;

  @Schema(description = """
      Configuration string for monthly correlation calculations, formatted as 'default,min,max'.
      Represents default rolling window size, minimum window size, and maximum window size in months.""")
  public final String monthlyConfiguration = GlobalConstants.CORR_MONTHLY;

  @Schema(description = """
      Configuration string for annual correlation calculations. If defined, it would follow a 'default,min,max' format for years.
      Refer to GlobalConstants.CORR_ANNUAL for current value (may be null).""")
  public final String annualConfiguration = GlobalConstants.CORR_ANNUAL;

  @Schema(description = "The minimum number of historical data periods (e.g., days or months) required to perform a valid correlation calculation.")
  public final byte requiredMinPeriods = GlobalConstants.REQUIRED_MIN_PERIODS;

  public CorrelationLimits(TenantLimit tenantLimit) {
    this.tenantLimit = tenantLimit;
  }
}
