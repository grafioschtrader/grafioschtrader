package grafioschtrader.platformimport;

import java.util.List;

import grafioschtrader.entities.ImportTransactionPos;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Contains form data and template validation results for checking import templates against document content")
public class FormTemplateCheck {
  @Schema(description = "Form data as text which is used to check the templates.")
  private String pdfAsTxt;

  @Schema(description = "Identifier of the transaction import platform")
  private Integer idTransactionImportPlatform;
  
  @Schema(description = "Transaction position information including file details and import status")
  private ImportTransactionPos importTransactionPos;
  
  @Schema(description = "Template that successfully parsed the form data, if any")
  private ParsedTemplateState successParsedTemplateState;
  
  @Schema(description = "List of templates that failed to parse the form data with diagnostic information")
  private List<FailedParsedTemplateState> failedParsedTemplateStateList;

  public String getPdfAsTxt() {
    return pdfAsTxt;
  }

  public void setPdfAsTxt(String pdfAsTxt) {
    this.pdfAsTxt = pdfAsTxt;
  }

  public Integer getIdTransactionImportPlatform() {
    return idTransactionImportPlatform;
  }

  public void setIdTransactionImportPlatform(Integer idTransactionImportPlatform) {
    this.idTransactionImportPlatform = idTransactionImportPlatform;
  }

  public ImportTransactionPos getImportTransactionPos() {
    return importTransactionPos;
  }

  public void setImportTransactionPos(ImportTransactionPos importTransactionPos) {
    this.importTransactionPos = importTransactionPos;
  }

  public List<FailedParsedTemplateState> getFailedParsedTemplateStateList() {
    return failedParsedTemplateStateList;
  }

  public void setFailedParseTemplateStateList(List<FailedParsedTemplateState> failedParseTemplateStateList) {
    this.failedParsedTemplateStateList = failedParseTemplateStateList;
  }

  public ParsedTemplateState getSuccessParsedTemplateState() {
    return successParsedTemplateState;
  }

  public void setSuccessParsedTemplateState(ParsedTemplateState successParsedTemplateState) {
    this.successParsedTemplateState = successParsedTemplateState;
  }

}
