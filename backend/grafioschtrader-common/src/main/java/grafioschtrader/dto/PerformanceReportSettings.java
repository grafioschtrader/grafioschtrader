package grafioschtrader.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import grafiosch.common.DynamicFormField;
import grafiosch.dynamic.model.DynamicFormPropertyHelps;
import grafioschtrader.types.PerformanceReportPreset;
import grafioschtrader.types.PerformanceReportSection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = """
    PDF dialog settings shared by the tenant. The one-off comment and the reporting dates are deliberately absent.
    A custom preset remembers the selected sections; other presets take their defaults from the server.""")
public class PerformanceReportSettings implements Serializable {
  // Tenant.reportSettings is JSON; Hibernate deep-copies it through Java serialization.
  private static final long serialVersionUID = 1L;

  public static final String ADDRESS_PATTERN = "[^\\r\\n]{0,45}(?:\\r?\\n[^\\r\\n]{0,45}){0,5}";
  public static final List<String> NUMBER_FORMATS = List.of("de-CH", "de-DE", "de-AT", "en-US", "en-GB", "fr-CH",
      "it-CH");

  @NotNull
  @DynamicFormField(uiOrder = "1", labelKey = "REPORT_PRESET", helps = DynamicFormPropertyHelps.SELECT_OPTIONS)
  @Schema(description = "Starting selection of document sections")
  public PerformanceReportPreset preset = PerformanceReportPreset.PERFORMANCE_DETAIL;

  @Schema(description = "Remembered section selection for CUSTOM")
  @NotEmpty
  public Set<@NotNull PerformanceReportSection> sections;

  @Size(max = 80)
  @DynamicFormField(uiOrder = "6", labelKey = "REPORT_TITLE")
  @Schema(description = "Optional title, otherwise the translated default title")
  public String title;

  @Size(max = 280)
  @Pattern(regexp = ADDRESS_PATTERN)
  @DynamicFormField(uiOrder = "7", labelKey = "REPORT_RECIPIENT")
  @Schema(description = "Envelope recipient, at most six lines of 45 characters")
  public String recipient;

  @Size(max = 280)
  @Pattern(regexp = ADDRESS_PATTERN)
  @DynamicFormField(uiOrder = "8", labelKey = "REPORT_SENDER")
  @Schema(description = "Sender or advisor, at most six lines of 45 characters")
  public String sender;

  @Size(max = 2000)
  @DynamicFormField(uiOrder = "9", labelKey = "REPORT_ADDITIONAL_NOTES")
  @Schema(description = "Notes printed after the mandatory disclaimer")
  public String additionalNotes;

  @NotNull
  @DynamicFormField(uiOrder = "2", labelKey = "REPORT_LANGUAGE", helps = DynamicFormPropertyHelps.SELECT_OPTIONS)
  @Schema(description = "Report language, validated against G_LANGUAGE_CODES")
  public String language;

  @NotNull
  @DynamicFormField(uiOrder = "3", labelKey = "REPORT_NUMBER_FORMAT", helps = DynamicFormPropertyHelps.SELECT_OPTIONS)
  @Schema(description = "Locale tag for dates and numbers, from the report options endpoint")
  public String numberFormat;

  @Min(1)
  @Max(20)
  @DynamicFormField(uiOrder = "4")
  @Schema(description = "Number of calendar years including the final year")
  public int annualYears = 10;

  @DynamicFormField(uiOrder = "5")
  @Schema(description = "Include optional holdings columns when that section is available")
  public boolean detailColumns;
}
