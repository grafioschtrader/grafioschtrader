package grafioschtrader.platformimport;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Metadata about a transaction import template, containing its purpose and validity information")
public class ParsedTemplateState {
  @Schema(description = "Human-readable description of what this template is used for (e.g., 'Swissquote Buy/Sell Orders')")
  private String templatePurpose;
  
  @Schema(description = "Date when this template version became valid for processing documents")
  private Date validSince;

  public ParsedTemplateState(String templatePurpose, Date validSince) {
    this.templatePurpose = templatePurpose;
    this.validSince = validSince;
  }

  public String getTemplatePurpose() {
    return templatePurpose;
  }

  public void setTemplatePurpose(String templatePurpose) {
    this.templatePurpose = templatePurpose;
  }

  public Date getValidSince() {
    return validSince;
  }

  public void setValidSince(Date validSince) {
    this.validSince = validSince;
  }

}
