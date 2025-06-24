package grafioschtrader.platform;

import java.util.List;
import java.util.Locale;

import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.repository.ImportTransactionPosFailedJpaRepository;
import grafioschtrader.repository.ImportTransactionPosJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
* Interface defining the contract for trading platform-specific transaction import implementations.
* 
* <p>This interface standardizes how different trading platforms handle document import across various file formats.
* Each trading platform (Swissquote, DEGIRO, Interactive Brokers, etc.) provides its own implementation while 
* maintaining consistent behavior for CSV, PDF, and batch-processed document imports. The interface ensures
* uniform integration with the GT import system regardless of platform-specific parsing requirements.</p>
* 
* <h3>Import Methods by File Type</h3>
* 
* <h4>CSV Import</h4>
* <p>Handles tabular transaction data exported from trading platforms. CSV imports can target specific
* templates when multiple templates are available for the same platform.</p>
* 
* <h4>PDF Import</h4>
* <p>Processes trading platform statements and confirmations in PDF format:</p>
* <ul>
*   <li><b>Single PDF</b> - Individual transaction documents (confirmations, statements)</li>
*   <li><b>Multiple PDF</b> - Batch processing of multiple PDF documents</li>
* </ul>
* 
* <h4>GT-Transform Import</h4>
* <p>Handles text files produced by GT-PDF-Transform containing multiple converted and anonymized
* PDF documents in a single batch file. Ideal for initial imports of historical transaction data.</p>
* 
* <h3>Template-Based Parsing</h3>
* <p>All import methods use template-based parsing where each platform can have multiple templates
* to handle different document formats or format changes over time. Templates define field positions,
* data types, and validation rules specific to each trading platform's document structure.</p>
* 
* <h3>Error Handling and Persistence</h3>
* <p>Import methods are responsible for:</p>
* <ul>
*   <li>Parsing documents according to platform-specific templates</li>
*   <li>Validating extracted transaction data</li>
*   <li>Persisting successful imports via ImportTransactionPosJpaRepository</li>
*   <li>Recording failed imports via ImportTransactionPosFailedJpaRepository</li>
*   <li>Resolving security instruments via SecurityJpaRepository</li>
* </ul>
*/
public interface IPlatformTransactionImport {

  /**
   * Returns the unique platform identifier with standard namespace prefix.
   * 
   * @return Platform ID (e.g., "gt.platform.import.swissquote")
   */
  String getID();

  /**
   * Returns the human-readable platform name for display in user interfaces.
   * 
   * @return Platform display name (e.g., "Swissquote Bank Ltd.")
   */
  String getReadableName();

  /**
   * Imports transaction data from a CSV file using platform-specific parsing logic.
   * 
   * @param importTransactionHead Import group container for this import session
   * @param uploadFile CSV file containing transaction data
   * @param importTransactionTemplateList Available CSV templates for this platform
   * @param importTransactionPosJpaRepository Repository for persisting successful import positions
   * @param securityJpaRepository Repository for resolving security instruments
   * @param importTransactionPosFailedJpaRepository Repository for recording failed import attempts
   * @param userLocale User's locale for number and date formatting
   * @param idTransactionImportTemplate Specific template ID to use for parsing (optional)
   * @throws Exception if CSV parsing or data persistence fails
   */
  void importCSV(ImportTransactionHead importTransactionHead, MultipartFile uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale,
      Integer idTransactionImportTemplate) throws Exception;

  /**
   * Imports transaction data from a single PDF document using platform-specific parsing templates.
   * 
   * @param importTransactionHead Import group container for this import session
   * @param uploadFile PDF file containing transaction document
   * @param importTransactionTemplateList Available PDF templates for this platform
   * @param importTransactionPosJpaRepository Repository for persisting successful import positions
   * @param securityJpaRepository Repository for resolving security instruments
   * @param importTransactionPosFailedJpaRepository Repository for recording failed import attempts
   * @param userLocale User's locale for number and date formatting
   * @throws Exception if PDF parsing or data persistence fails
   */
  void importSinglePdfAsPdf(ImportTransactionHead importTransactionHead, MultipartFile uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale)
      throws Exception;

  /**
   * Imports transaction data from multiple PDF documents in a single batch operation.
   * 
   * @param importTransactionHead Import group container for this import session
   * @param uploadFile Array of PDF files containing transaction documents
   * @param importTransactionTemplateList Available PDF templates for this platform
   * @param importTransactionPosJpaRepository Repository for persisting successful import positions
   * @param securityJpaRepository Repository for resolving security instruments
   * @param importTransactionPosFailedJpaRepository Repository for recording failed import attempts
   * @param userLocale User's locale for number and date formatting
   * @throws Exception if PDF parsing or data persistence fails
   */
  void importMultiplePdfAsPdf(ImportTransactionHead importTransactionHead, MultipartFile[] uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale)
      throws Exception;

  /**
   * Imports transaction data from a text file produced by GT-PDF-Transform containing multiple converted PDF documents.
   * This method handles batch-processed files where multiple PDF documents have been converted to text format
   * and potentially anonymized for privacy protection.
   * 
   * @param importTransactionHead Import group container for this import session
   * @param uploadFile Text file containing multiple converted PDF documents from GT-PDF-Transform
   * @param importTransactionTemplateList Available templates for parsing the converted PDF content
   * @param importTransactionPosJpaRepository Repository for persisting successful import positions
   * @param securityJpaRepository Repository for resolving security instruments
   * @param importTransactionPosFailedJpaRepository Repository for recording failed import attempts
   * @param userLocale User's locale for number and date formatting
   */
  void importGTTransform(ImportTransactionHead importTransactionHead, MultipartFile uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale);

}
