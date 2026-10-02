package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.common.DynamicFormField;
import grafioschtrader.reportviews.performance.WeekYear;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = """
    Read-only PDF generation request. Dates and selected sections are validated together before loading any report.
    dateFrom is the excluded valuation base; bookings begin on the next calendar day.""")
public class PerformanceReportRequest extends PerformanceReportSettings {
  private static final long serialVersionUID = 1L;

  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Schema(description = "Excluded valuation base of a performance period")
  public LocalDate dateFrom;
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Schema(description = "Last date of a performance period")
  public LocalDate dateTo;
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Schema(description = "Reporting date for sections that do not require a period; dateTo takes precedence when supplied")
  public LocalDate reportDate;
  @Schema(description = "Weekly or yearly aggregation, as on the period screen")
  public WeekYear periodSplit;
  @Schema(description = "Portfolio belonging to the context tenant, or null for the whole tenant")
  public Integer idPortfolio;
  @Size(max = 2000)
  @DynamicFormField(uiOrder = "10", labelKey = "REPORT_COMMENT")
  @Schema(description = "Comment for this document only; never remembered")
  public String comment;

  /** Date sections share the period end when a period was supplied. */
  public LocalDate valuationDate() {
    return dateTo == null ? reportDate : dateTo;
  }

  /**
   * Only the selected sections decide whether this is a performance report. Date sections alone make a statement of
   * assets, also when the dialog was opened from a period and therefore carries its dates.
   */
  public boolean hasPeriod() {
    return sections != null && sections.stream().anyMatch(s -> s != null && s.needsPeriod());
  }
}
