package grafiosch.dto;

import grafiosch.common.DynamicFormField;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Common bounded list configuration, also the source of the configuration form. */
public class DashboardListConfig {
  @DynamicFormField(uiOrder = "1.1", labelKey = "DASHBOARD_MAX_ROWS")
  @NotNull
  @Min(1)
  @Max(20)
  private Integer maxRows = 5;

  public Integer getMaxRows() {
    return maxRows;
  }

  public void setMaxRows(Integer maxRows) {
    this.maxRows = maxRows;
  }
}
