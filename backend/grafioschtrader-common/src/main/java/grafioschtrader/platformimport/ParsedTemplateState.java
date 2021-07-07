package grafioschtrader.platformimport;

import java.util.Date;

/**
 * Contains a part of the ImportTransactionTemplate
 *
 * @author Hugo Graf
 *
 */
public class ParsedTemplateState {
  private String templatePurpose;
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
