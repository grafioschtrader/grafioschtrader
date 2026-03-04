package grafioschtrader.platformimport;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Metadata about a transaction import template, containing its purpose and validity information")
public class ParsedTemplateState {
  @Schema(description = "Human-readable description of what this template is used for (e.g., 'Swissquote Buy/Sell Orders')")
  private String templatePurpose;

  @Schema(description = "Date when this template version became valid for processing documents")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT)
  private LocalDate validSince;

  public ParsedTemplateState(String templatePurpose, LocalDate validSince) {
    this.templatePurpose = templatePurpose;
    this.validSince = validSince;
  }

  public String getTemplatePurpose() {
    return templatePurpose;
  }

  public void setTemplatePurpose(String templatePurpose) {
    this.templatePurpose = templatePurpose;
  }

  public LocalDate getValidSince() {
    return validSince;
  }

  public void setValidSince(LocalDate validSince) {
    this.validSince = validSince;
  }

}
