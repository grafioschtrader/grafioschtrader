package grafioschtrader.entities;

import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.Assert;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.Auditable;
import grafioschtrader.platformimport.TemplateConfiguration;
import grafioschtrader.types.TemplateCategory;
import grafioschtrader.types.TemplateFormatType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

// @JsonFilter("NON_TEMPLATE")
@Schema(description = """
    As each trading platform implements its own document design, one or more import templates
    must be created in GT according to these documents. It contains the pdf or csv template as text,
    which will be used for import transactions.""")
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

  @Schema(description = "The purpose of the template.")
  @Column(name = "template_purpose")
  @PropertyAlwaysUpdatable
  private String templatePurpose;

  @Schema(description = "The category is also used for importing templates.")
  @Column(name = "template_category")
  @PropertyAlwaysUpdatable
  private byte templateCategory;

  @Schema(description = "Document type which are supported by the import of transactions.")
  @Column(name = "template_format_type")
  @PropertyAlwaysUpdatable
  private byte templateFormatType;

  @Schema(description = """
      The template is valid from this date, but this is only used if a transaction import matches several templates.
      In this case, the template with the most recent date compared to the date in the transaction import is used.""")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Temporal(TemporalType.DATE)
  @Column(name = "valid_since")
  @PropertyAlwaysUpdatable
  private Date validSince;

  @Schema(description = """
      The same import template is differentiated by language. Has an informative character for the user
      and is used for differentiation when importing and exporting import templates.""")
  @Column(name = "template_language")
  @PropertyAlwaysUpdatable
  private String templateLanguage;

  @Schema(description = "Contains the import template as text.")
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
