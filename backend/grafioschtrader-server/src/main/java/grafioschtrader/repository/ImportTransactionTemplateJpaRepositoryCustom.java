package grafioschtrader.repository;

import java.util.List;
import java.util.Locale;

import org.springframework.web.multipart.MultipartFile;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.dto.SuccessFailedImportTransactionTemplate;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platformimport.FormTemplateCheck;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Custom repository interface for import transaction template management and validation operations.
 * 
 * <p>This interface extends the base repository functionality to provide specialized operations for
 * managing import transaction templates, which define how to parse and interpret financial transaction
 * data from various sources (CSV files, PDF documents). Templates serve as the bridge between raw
 * imported data and structured transaction records, enabling automatic parsing and validation of
 * diverse trading platform formats.</p>
 * 
 * <h3>Core Functionality</h3>
 * 
 * <h4>Template Validation and Matching</h4>
 * <p>Provides sophisticated template matching capabilities that can parse form data against
 * multiple templates to identify the correct parsing rules and extract transaction information.</p>
 * 
 * <h4>Template Management</h4>
 * <p>Supports comprehensive template lifecycle management including creation, validation,
 * export, and bulk upload operations with detailed success/failure reporting.</p>
 * 
 * <h4>Localization Support</h4>
 * <p>Enables multi-language template support with locale-aware validation and language
 * selection capabilities for international trading platforms.</p>
 * 
 * <h4>Platform Integration</h4>
 * <p>Facilitates integration with various trading platforms by providing template export
 * and import capabilities, allowing template sharing and deployment across environments.</p>
 * 
 * <h3>Template Types and Formats</h3>
 * <p>The repository supports templates for different data formats:</p>
 * <ul>
 *   <li><b>CSV Templates:</b> For parsing structured comma-separated value files</li>
 *   <li><b>PDF Templates:</b> For extracting data from PDF transaction statements</li>
 *   <li><b>Multi-language Templates:</b> Supporting various locale-specific formats</li>
 * </ul>
 * 
 * @see ImportTransactionTemplate
 * @see FormTemplateCheck
 * @see BaseRepositoryCustom
 */
public interface ImportTransactionTemplateJpaRepositoryCustom extends BaseRepositoryCustom<ImportTransactionTemplate> {
  FormTemplateCheck checkFormAgainstTemplate(FormTemplateCheck formTemplateCheck, Locale userLocale) throws Exception;

  /**
   * Retrieves all available languages that can be used for template creation and localization. This method provides a
   * comprehensive list of supported locales, enabling users to create templates for different language-specific trading
   * platform formats.
   * 
   * <p>
   * The returned language options are:
   * </p>
   * <ul>
   * <li>Filtered to unique language codes (avoiding duplicates)</li>
   * <li>Displayed in the user's locale for better usability</li>
   * <li>Sorted alphabetically for easy selection</li>
   * </ul>
   * 
   * @return List of language options suitable for HTML select elements, containing language codes as keys and localized
   *         language names as display values
   */
  List<ValueKeyHtmlSelectOptions> getPossibleLanguagesForTemplate();

  /**
   * Retrieves CSV template identifiers and descriptions as HTML select options for a specific platform. This method
   * provides a user-friendly list of available CSV templates that can be used for import operations, displaying both
   * template IDs and descriptive purposes.
   * 
   * <p>
   * The returned options include:
   * </p>
   * <ul>
   * <li>Template ID as the option value for form submissions</li>
   * <li>Combined template ID and purpose as the display text</li>
   * <li>Only templates associated with the specified platform</li>
   * </ul>
   * 
   * @param idTransactionImportPlatform The platform ID to retrieve templates for
   * @return List of HTML select options with template IDs and descriptions for user selection
   */
  List<ValueKeyHtmlSelectOptions> getCSVTemplateIdsAsValueKeyHtmlSelectOptions(Integer idTransactionImportPlatform);

  /**
   * Exports all templates for a specific platform as a ZIP archive for backup or deployment purposes. This method
   * creates a downloadable ZIP file containing all templates associated with the specified platform, formatted with
   * standardized filenames that include metadata for easy identification.
   * 
   * <p>
   * The ZIP archive contains:
   * </p>
   * <ul>
   * <li>All templates for the specified platform</li>
   * <li>Standardized filenames: category-format-date-language.tmpl</li>
   * <li>Template content with purpose information embedded</li>
   * <li>Proper HTTP headers for file download</li>
   * </ul>
   * 
   * <p>
   * Filename format example: accumulate-csv-2024-01-15-en.tmpl
   * </p>
   * 
   * @param idTransactionImportPlatform The platform ID whose templates to export
   * @param response                    HTTP response object for streaming the ZIP file to the client
   */
  void getTemplatesByPlatformPlanAsZip(Integer idTransactionImportPlatform, HttpServletResponse response);

  /**
   * Uploads and processes multiple template files for a specific platform with detailed result reporting. This method
   * handles bulk template upload operations, parsing filenames for metadata, validating template content, and creating
   * or updating template records with comprehensive error tracking.
   * 
   * <p>
   * Upload processing includes:
   * </p>
   * <ul>
   * <li>Filename parsing for template metadata (category, format, date, language)</li>
   * <li>Template content validation and purpose extraction</li>
   * <li>Duplicate detection and update handling</li>
   * <li>Permission validation for template ownership</li>
   * <li>Detailed success/failure tracking for each file</li>
   * </ul>
   * 
   * <p>
   * Expected filename format: category-format-date-language.tmpl
   * </p>
   * <p>
   * Example: dividend-pdf-2024-01-15-de.tmpl
   * </p>
   * 
   * @param idTransactionImportPlatform The platform ID to associate uploaded templates with
   * @param uploadFiles                 Array of template files to process and import
   * @return Detailed statistics about the upload operation including success counts and error categories
   * @throws Exception if critical errors occur during template processing or validation
   */
  SuccessFailedImportTransactionTemplate uploadImportTemplateFiles(Integer idTransactionImportPlatform,
      MultipartFile[] uploadFiles) throws Exception;

  
}
