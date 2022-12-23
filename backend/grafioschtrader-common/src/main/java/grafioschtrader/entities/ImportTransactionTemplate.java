package grafioschtrader.entities;

import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.Assert;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.platformimport.TemplateConfiguration;
import grafioschtrader.types.TemplateCategory;
import grafioschtrader.types.TemplateFormatType;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

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

  private static final String TEMPLATE_PURPOSE = "templatePurpose=";
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

  @Column(name = "template_category")
  @PropertyAlwaysUpdatable
  private byte templateCategory;

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

  public ImportTransactionTemplate() {
  }

  public ImportTransactionTemplate(TemplateCategory templateCategory, TemplateFormatType templateFormatType,
      String templateLanguage) {
    this.templateCategory = templateCategory.getValue();
    this.templateFormatType = templateFormatType.getValue();
    this.templateLanguage = templateLanguage;
  }

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

  public TemplateCategory getTemplateCategory() {
    return TemplateCategory.getTemplateCategory(templateCategory);
  }

  public void setTemplateCategory(TemplateCategory templateCategory) {
    Assert.notNull(templateCategory, "Template category mustn't be null!");
    this.templateCategory = templateCategory.getValue();
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

  @JsonIgnore
  public boolean isLanguageLocaleOK() {
    return Arrays.stream(Locale.getAvailableLocales()).anyMatch(loc -> loc.getLanguage().equals(templateLanguage));
  }

  public boolean copyPurposeInTextToFieldPurpose() {
    if (templateAsTxt != null) {
      Pattern purposeP = Pattern.compile("^" + TEMPLATE_PURPOSE + "(.*)$", Pattern.MULTILINE);
      Matcher m = purposeP.matcher(templateAsTxt);
      if (m.find()) {
        templatePurpose = m.group(1);
        return true;
      }
    }
    return false;

  }

  public void replacePurposeInTemplateAsText() {
    // remove existing purpose
    templateAsTxt = templateAsTxt.replaceFirst(TEMPLATE_PURPOSE + ".*(?:\\r?\\n)?", "");
    // add purpose
    templateAsTxt = templateAsTxt.replaceFirst(Pattern.quote(TemplateConfiguration.SECTION_END),
        TemplateConfiguration.SECTION_END + System.lineSeparator() + TEMPLATE_PURPOSE + this.templatePurpose);
  }

  @Override
  public String toString() {
    return "ImportTransactionTemplate [idTransactionImportTemplate=" + idTransactionImportTemplate
        + ", idTransactionImportPlatform=" + idTransactionImportPlatform + ", templatePurpose=" + templatePurpose
        + ", templateFormatType=" + templateFormatType + ", validSince=" + validSince + ", templateLanguage="
        + templateLanguage + ", templateAsTxt=" + templateAsTxt + "]";
  }

}
