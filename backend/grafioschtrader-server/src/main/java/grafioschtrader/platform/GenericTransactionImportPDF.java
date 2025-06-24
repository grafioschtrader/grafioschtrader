package grafioschtrader.platform;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionPosFailed;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.platformimport.pdf.ImportTransactionHelperPdf;
import grafioschtrader.platformimport.pdf.ParseFormInputPDFasTXT;
import grafioschtrader.platformimport.pdf.TemplateConfigurationPDFasTXT;
import grafioschtrader.repository.ImportTransactionPosFailedJpaRepository;
import grafioschtrader.repository.ImportTransactionPosJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Generic PDF transaction importer supporting individual PDFs, batch PDF processing, and GT-Transform files.
 * 
 * <p>This class provides a universal PDF import solution that can handle transaction documents from most
 * online brokers. It supports three distinct import workflows: individual PDF documents, multiple PDF
 * batch processing, and GT-Transform text files containing multiple converted PDF documents. The importer
 * uses template-based parsing to extract transaction data from PDF content converted to text format.</p>
 * 
 * <h3>Import Workflows</h3>
 * 
 * <h4>Individual PDF Import</h4>
 * <p>Processes single PDF documents (confirmations, statements) by:</p>
 * <ul>
 *   <li>Converting PDF to text using built-in PDF processing</li>
 *   <li>Applying configurable text cleaning via {@link #cleanReadPDF(String)}</li>
 *   <li>Matching against available templates for transaction extraction</li>
 *   <li>Creating import positions for successful matches</li>
 * </ul>
 * 
 * <h4>Batch PDF Processing</h4>
 * <p>Handles multiple PDF files in a single operation, processing each document independently
 * while maintaining consistent error handling and template matching across all files.</p>
 * 
 * <h4>GT-Transform Import</h4>
 * <p>Processes text files created by GT-PDF-Transform containing multiple converted PDF documents.
 * The format uses file markers {@code [fileNumber|filename]} to separate individual documents
 * within the batch file, enabling import of large historical transaction sets.</p>
 * 
 * <h3>Template-Based Parsing</h3>
 * <p>Uses {@link TemplateConfigurationPDFasTXT} templates that define:</p>
 * <ul>
 *   <li>Field extraction patterns using regular expressions</li>
 *   <li>Data validation rules for transaction properties</li>
 *   <li>Platform-specific formatting and layout recognition</li>
 *   <li>Multi-line transaction parsing for complex documents</li>
 * </ul>
 * 
 * <h3>Enhanced Dividend Processing</h3>
 * <p>Extends the base class functionality with automatic exchange rate calculation for dividend transactions
 * involving currency conversion, ensuring accurate multi-currency portfolio tracking.</p>
 * 
 * <h3>Customization</h3>
 * <p>Platform-specific implementations can override {@link #cleanReadPDF(String)} to apply
 * custom text cleaning logic for PDF documents that require preprocessing before template matching.</p>
 */
public class GenericTransactionImportPDF extends GenericTransactionImportCsvPdfBase {

  /**
   * Creates a new generic PDF transaction importer with the specified import context.
   * 
   * @param importTransactionHead         Import session container with portfolio and account information
   * @param importTransactionTemplateList Available PDF templates for parsing transaction documents
   */
  public GenericTransactionImportPDF(final ImportTransactionHead importTransactionHead,
      List<ImportTransactionTemplate> importTransactionTemplateList) {
    super(importTransactionHead, importTransactionTemplateList);
  }

  /**
   * Imports transaction data from multiple PDF files in a single batch operation. Each PDF is processed independently
   * using the same template matching logic, with errors isolated to individual files to prevent batch failures.
   * 
   * @param uploadFiles                             Array of PDF files to process
   * @param importTransactionPosJpaRepository       Repository for persisting successful import positions
   * @param securityJpaRepository                   Repository for resolving security instruments
   * @param importTransactionPosFailedJpaRepository Repository for recording failed import attempts
   * @param userLocale                              User's locale for number and date formatting
   * @throws Exception if batch processing setup fails
   */
  public void importMultiplePdfForm(MultipartFile[] uploadFiles,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale)
      throws Exception {
    Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap = ImportTransactionHelperPdf
        .readTemplates(importTransactionTemplateList, userLocale);

    for (MultipartFile uploadFile : uploadFiles) {
      this.parseSinglePdfForm(templateScannedMap, uploadFile, importTransactionPosJpaRepository, securityJpaRepository,
          importTransactionPosFailedJpaRepository);
    }
  }

  /**
   * Imports transaction data from a single PDF document using template-based parsing. Converts the PDF to text, applies
   * optional cleaning, and attempts to match against available templates to extract transaction information.
   * 
   * @param uploadFile                              PDF file containing transaction data
   * @param importTransactionPosJpaRepository       Repository for persisting successful import positions
   * @param securityJpaRepository                   Repository for resolving security instruments
   * @param importTransactionPosFailedJpaRepository Repository for recording failed import attempts
   * @param userLocale                              User's locale for number and date formatting
   * @throws Exception if PDF processing or template matching fails
   */
  public void importSinglePdfForm(MultipartFile uploadFile,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale)
      throws Exception {
    Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap = ImportTransactionHelperPdf
        .readTemplates(importTransactionTemplateList, userLocale);

    this.parseSinglePdfForm(templateScannedMap, uploadFile, importTransactionPosJpaRepository, securityJpaRepository,
        importTransactionPosFailedJpaRepository);
  }

  /**
   * Core PDF parsing logic that converts PDF to text and attempts template matching. Handles the complete workflow from
   * PDF conversion through transaction creation or failure recording. Uses the configured templates to extract
   * transaction properties.
   * 
   * @param templateScannedMap                      Available templates mapped to their configurations
   * @param uploadFile                              PDF file to process
   * @param importTransactionPosJpaRepository       Repository for persisting import positions
   * @param securityJpaRepository                   Repository for resolving security instruments
   * @param importTransactionPosFailedJpaRepository Repository for recording failures
   * @throws Exception if PDF processing fails
   */
  private void parseSinglePdfForm(Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap,
      MultipartFile uploadFile, ImportTransactionPosJpaRepository importTransactionPosJpaRepository,
      SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository) throws Exception {
    List<ImportProperties> importPropertiesList = null;
    if (!uploadFile.isEmpty()) {
      try (InputStream is = uploadFile.getInputStream()) {
        ParseFormInputPDFasTXT parseInputPDFasTXT = new ParseFormInputPDFasTXT(
            cleanReadPDF(ImportTransactionHelperPdf.transFormPDFToTxt(is)), templateScannedMap);
        importPropertiesList = parseInputPDFasTXT.parseInput();
        if (importPropertiesList != null) {
          Portfolio portfolio = importTransactionHead.getSecurityaccount().getPortfolio();
          // Found matching template
          ImportTransactionTemplate importTransactionTemplate = parseInputPDFasTXT
              .getSuccessTemplate(importPropertiesList);
          checkAndSaveSuccessImportTransaction(importTransactionTemplate, portfolio.getCashaccountList(),
              importPropertiesList, uploadFile.getOriginalFilename(), importTransactionPosJpaRepository,
              securityJpaRepository);
        } else {
          this.failedParse(parseInputPDFasTXT, uploadFile.getOriginalFilename(), null,
              importTransactionPosJpaRepository, importTransactionPosFailedJpaRepository);
        }
      }
    }
  }

  /**
   * Enhanced cash account assignment with automatic exchange rate calculation for dividend transactions. Extends the
   * base implementation to handle multi-currency dividend scenarios where exchange rates need to be determined
   * automatically for accurate portfolio tracking.
   * 
   * @param cashaccountList                   Available cash accounts in the portfolio
   * @param importTransactionPos              Import position to validate and prepare
   * @param importTransactionPosJpaRepository Repository for transaction validation and exchange rate calculation
   */
  @Override
  protected void setCashaccountAndCheckReadyState(List<Cashaccount> cashaccountList,
      ImportTransactionPos importTransactionPos, ImportTransactionPosJpaRepository importTransactionPosJpaRepository) {
    super.setCashaccountAndCheckReadyState(cashaccountList, importTransactionPos, importTransactionPosJpaRepository);
    importTransactionPosJpaRepository.addPossibleExchangeRateForDividend(importTransactionHead, importTransactionPos);

  }

  /**
   * Hook method for applying custom text cleaning to PDF content before template matching. Platform-specific
   * implementations can override this method to remove or modify text patterns that interfere with template parsing
   * (headers, footers, formatting artifacts).
   * 
   * @param readPDFAsText Raw text extracted from the PDF document
   * @return Cleaned text ready for template matching
   */
  protected String cleanReadPDF(String readPDFAsText) {
    return readPDFAsText;
  }

  /**
   * Imports transaction data from a GT-PDF-Transform text file containing multiple converted PDF documents. Processes
   * the batch format where individual PDF documents are separated by file markers {@code [fileNumber|filename]} and
   * handles each document as a separate transaction.
   * 
   * @param uploadFile                              Text file from GT-PDF-Transform containing multiple PDF documents
   * @param importTransactionPosJpaRepository       Repository for persisting import positions
   * @param securityJpaRepository                   Repository for resolving security instruments
   * @param importTransactionPosFailedJpaRepository Repository for recording failures
   * @param userLocale                              User's locale for number and date formatting
   */
  public void importGTTransform(MultipartFile uploadFile,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale) {

    Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap = ImportTransactionHelperPdf
        .readTemplates(importTransactionTemplateList, userLocale);
    parseGTTransform(uploadFile, templateScannedMap, importTransactionPosJpaRepository, securityJpaRepository,
        importTransactionPosFailedJpaRepository);
  }

  /**
   * Parses GT-Transform batch file format by identifying individual PDF documents using file markers. The format uses
   * {@code [fileNumber|filename]} markers to separate documents within the batch file. Each identified document is
   * processed independently with its own transaction scope.
   * 
   * @param uploadFile                              GT-Transform text file to parse
   * @param templateScannedMap                      Available templates for document parsing
   * @param importTransactionPosJpaRepository       Repository for persisting import positions
   * @param securityJpaRepository                   Repository for resolving security instruments
   * @param importTransactionPosFailedJpaRepository Repository for recording failures
   */
  private void parseGTTransform(MultipartFile uploadFile,
      Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository) {
    Pattern startNewFormPatter = Pattern.compile("^\\[(\\d+)\\|(.*)\\]$");
    int lastFileNumber = 0;
    String lastFile = null;
    if (!uploadFile.isEmpty()) {
      Portfolio portfolio = importTransactionHead.getSecurityaccount().getPortfolio();

      try (InputStream is = uploadFile.getInputStream();
          BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        StringBuilder singleFormSB = null;

        while (reader.ready()) {
          String line = reader.readLine();
          Matcher matcher = startNewFormPatter.matcher(line);
          if (matcher.find()) {
            if (singleFormSB != null) {
              parseAndSaveImportTransactionPos(portfolio.getCashaccountList(), templateScannedMap, singleFormSB,
                  lastFileNumber, lastFile, importTransactionPosJpaRepository, securityJpaRepository,
                  importTransactionPosFailedJpaRepository);
            }
            lastFileNumber = Integer.parseInt(matcher.group(1));
            lastFile = matcher.group(2);
            singleFormSB = new StringBuilder("");
          } else {
            singleFormSB.append(line).append(System.lineSeparator());
          }

        }
        if (singleFormSB != null) {
          // For the last form
          parseAndSaveImportTransactionPos(portfolio.getCashaccountList(), templateScannedMap, singleFormSB,
              lastFileNumber, lastFile, importTransactionPosJpaRepository, securityJpaRepository,
              importTransactionPosFailedJpaRepository);
        }
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  /**
   * Processes an individual PDF document extracted from a GT-Transform batch file. Each PDF form is processed within
   * its own transaction to ensure proper error isolation and maintain data consistency when processing large batches of
   * historical documents.
   * 
   * @param cashaccountList                         Available cash accounts for transaction assignment
   * @param templateScannedMap                      Available templates for document parsing
   * @param singleFormSB                            Content of a single PDF document as text
   * @param lastFileNumber                          File number from the GT-Transform batch marker
   * @param lastFile                                Original filename from the GT-Transform batch marker
   * @param importTransactionPosJpaRepository       Repository for persisting import positions
   * @param securityJpaRepository                   Repository for resolving security instruments
   * @param importTransactionPosFailedJpaRepository Repository for recording failures
   */
  @Transactional
  @Modifying
  private void parseAndSaveImportTransactionPos(List<Cashaccount> cashaccountList,
      Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap, StringBuilder singleFormSB,
      int lastFileNumber, String lastFile, ImportTransactionPosJpaRepository importTransactionPosJpaRepository,
      SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository) {
    try {
      ParseFormInputPDFasTXT parseInputPDFasTXT = new ParseFormInputPDFasTXT(singleFormSB.toString(),
          templateScannedMap);
      List<ImportProperties> importPropertiesList = parseInputPDFasTXT.parseInput(lastFileNumber);
      if (importPropertiesList != null) {
        // Found matching template
        ImportTransactionTemplate importTransactionTemplate = parseInputPDFasTXT
            .getSuccessTemplate(importPropertiesList);

        checkAndSaveSuccessImportTransaction(importTransactionTemplate, cashaccountList, importPropertiesList, lastFile,
            importTransactionPosJpaRepository, securityJpaRepository);
      } else {
        this.failedParse(parseInputPDFasTXT, lastFile, lastFileNumber, importTransactionPosJpaRepository,
            importTransactionPosFailedJpaRepository);
      }

    } catch (Exception e) {
      // TODO
      e.printStackTrace();
    }
  }

  /**
   * Records parsing failures with detailed diagnostic information for template debugging. Creates import position
   * records for failed attempts along with detailed failure information showing which templates were attempted and
   * where parsing failed for troubleshooting.
   * 
   * @param parseInputPDFasTXT                      Parser containing failure diagnostic information
   * @param orginalFileName                         Original name of the failed document
   * @param lastFileNumber                          File number for GT-Transform batch files (null for single files)
   * @param importTransactionPosJpaRepository       Repository for creating failure records
   * @param importTransactionPosFailedJpaRepository Repository for storing detailed failure information
   */
  private void failedParse(ParseFormInputPDFasTXT parseInputPDFasTXT, String orginalFileName, Integer lastFileNumber,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository) {
    ImportTransactionPos importTransactionPos = new ImportTransactionPos(importTransactionHead.getIdTenant(),
        orginalFileName, importTransactionHead.getIdTransactionHead());
    importTransactionPos.setIdFilePart(lastFileNumber);
    importTransactionPos = importTransactionPosJpaRepository.save(importTransactionPos);
    List<ImportTransactionPosFailed> importTransactionPosFailedList = parseInputPDFasTXT
        .getImportTransactionPosFailed(importTransactionPos.getIdTransactionPos());
    importTransactionPosFailedJpaRepository.saveAll(importTransactionPosFailedList);
  }

}
