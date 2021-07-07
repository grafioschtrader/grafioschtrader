package grafioschtrader.platformimport;

import java.util.List;

import grafioschtrader.entities.ImportTransactionPos;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A template can be checked against form data.
 *
 *
 */
public class FormTemplateCheck {
  @Schema(description = "Form data as text which is used to check the templates.")
  private String pdfAsTxt;

  private Integer idTransactionImportPlatform;
  private ImportTransactionPos importTransactionPos;
  private ParsedTemplateState successParsedTemplateState;
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
