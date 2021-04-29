package grafioschtrader.entities;

import java.util.Date;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.types.TemplateFormatType;

/**
 * It contains the pdf or csv template as text, which will be used for import
 * transactions.
 * 
 * @author Hugo Graf
 *
 */
// @JsonFilter("NON_TEMPLATE")
@Entity
@Table(name = ImportTransactionTemplate.TABNAME)
public class ImportTransactionTemplate extends Auditable {

  public static final String TABNAME = "imp_trans_template";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_trans_imp_template")
  private Integer idTransactionImportTemplate;

  @Column(name = "id_trans_imp_platform")
  private Integer idTransactionImportPlatform;

  @Column(name = "template_purpose")
  @PropertyAlwaysUpdatable
  private String templatePurpose;

  @Column(name = "template_format_type")
  @PropertyAlwaysUpdatable
  private byte templateFormatType;

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Temporal(TemporalType.DATE)
  @Column(name = "valid_since")
  @PropertyAlwaysUpdatable
  private Date validSince;

  @Column(name = "template_language")
  @PropertyAlwaysUpdatable
  private String templateLanguage;

  @Column(name = "template_as_txt")
  @PropertyAlwaysUpdatable
  private String templateAsTxt;

  public Integer getIdTransactionImportTemplate() {
    return idTransactionImportTemplate;
  }

  public void setIdTransactionImportTemplate(Integer idTransactionImportTemplate) {
    this.idTransactionImportTemplate = idTransactionImportTemplate;
  }

  public Integer getIdTransactionImportPlatform() {
    return idTransactionImportPlatform;
  }

  public void setIdTransactionImportPlatform(Integer idTransactionImportPlatform) {
    this.idTransactionImportPlatform = idTransactionImportPlatform;
  }

  public TemplateFormatType getTemplateFormatType() {
    return TemplateFormatType.getTemplateFormatTypeByValue(templateFormatType);
  }

  public void setTemplateFormatType(TemplateFormatType templateFormatType) {
    this.templateFormatType = templateFormatType.getValue();
  }

  public String getTemplatePurpose() {
    return templatePurpose;
  }

  public void setTemplatePurpose(String templatePurpose) {
    this.templatePurpose = templatePurpose;
  }

  public String getTemplateAsTxt() {
    return templateAsTxt;
  }

  public void setTemplateAsTxt(String templateAsTxt) {
    this.templateAsTxt = templateAsTxt;
  }

  public Date getValidSince() {
    return validSince;
  }

  public void setValidSince(Date validSince) {
    this.validSince = validSince;
  }

  public String getTemplateLanguage() {
    return templateLanguage;
  }

  public void setTemplateLanguage(String templateLanguage) {
    this.templateLanguage = templateLanguage;
  }

  @Override
  public Integer getId() {
    return idTransactionImportTemplate;
  }

  @Override
  public String toString() {
    return "ImportTransactionTemplate [idTransactionImportTemplate=" + idTransactionImportTemplate
        + ", idTransactionImportPlatform=" + idTransactionImportPlatform + ", templatePurpose=" + templatePurpose
        + ", templateFormatType=" + templateFormatType + ", validSince=" + validSince + ", templateLanguage="
        + templateLanguage + ", templateAsTxt=" + templateAsTxt + "]";
  }

}
