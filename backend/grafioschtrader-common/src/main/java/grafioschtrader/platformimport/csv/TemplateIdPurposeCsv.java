package grafioschtrader.platformimport.csv;

/**
 * Projection interface for template import metadata, combining the template’s database identifier, its business
 * purpose, and the numeric template ID parsed from the template text.
 * <p>
 * Typically used with native queries that extract these three values into a DTO-like view.
 */
public interface TemplateIdPurposeCsv {

  /**
   * Returns the primary key of the import transaction template.
   *
   * @return the database ID of the import template
   */
  Integer getIdTransactionImportTemplate();

  /**
   * Returns the purpose or description of the template.
   *
   * @return a human-readable purpose string for the template
   */
  String getTemplatePurpose();

  /**
   * Returns the numeric template ID extracted from the template text.
   * <p>
   * This value is typically parsed from the raw template string (e.g. via a regular expression) and cast to an integer.
   *
   * @return the parsed integer template ID
   */
  Integer getTemplateId();
}
