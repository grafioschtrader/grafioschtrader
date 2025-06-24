package grafioschtrader.repository;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import grafiosch.common.DataHelper;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertySelectiveUpdatableOrWhenNull;
import grafiosch.common.UserAccessHelper;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.User;
import grafiosch.repository.BaseRepositoryImpl;
import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.SuccessFailedImportTransactionTemplate;
import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platform.TransactionImportHelper;
import grafioschtrader.platformimport.FormTemplateCheck;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.platformimport.ParsedTemplateState;
import grafioschtrader.platformimport.TemplateConfiguration;
import grafioschtrader.platformimport.csv.TemplateConfigurationAndStateCsv;
import grafioschtrader.platformimport.csv.TemplateIdPurposeCsv;
import grafioschtrader.platformimport.pdf.ImportTransactionHelperPdf;
import grafioschtrader.platformimport.pdf.ParseFormInputPDFasTXT;
import grafioschtrader.platformimport.pdf.TemplateConfigurationPDFasTXT;
import grafioschtrader.types.TemplateCategory;
import grafioschtrader.types.TemplateFormatType;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Implementation of custom repository operations for import transaction template management and processing.
 * 
 * <p>This implementation class provides the core business logic for managing import transaction templates,
 * which serve as parsing rules for converting raw financial data from various sources (CSV files, PDF documents)
 * into structured transaction records. The class handles template validation, storage, retrieval, and
 * sophisticated parsing operations against multiple template formats.</p>
 * 
 * <h3>Key Capabilities</h3>
 * 
 * <h4>Template Validation and Processing</h4>
 * <p>Provides comprehensive template validation ensuring templates are syntactically correct and
 * contain all required parsing rules before storage. Supports both CSV and PDF template formats
 * with format-specific validation logic.</p>
 * 
 * <h4>Multi-Format Template Support</h4>
 * <ul>
 *   <li><b>CSV Templates:</b> For parsing structured comma-separated value files with configurable
 *       column mappings and data transformation rules</li>
 *   <li><b>PDF Templates:</b> For extracting transaction data from PDF statements using text pattern
 *       matching and regular expressions</li>
 * </ul>
 * 
 * <h4>Template Lifecycle Management</h4>
 * <p>Supports complete template lifecycle including creation, validation, export for backup/deployment,
 * and bulk import with detailed success/failure tracking. Templates are versioned by date and support
 * multiple languages for international trading platforms.</p>
 * 
 * <h4>Platform Integration</h4>
 * <p>Enables seamless integration with various trading platforms by providing template export/import
 * capabilities with standardized filename formats for easy identification and deployment across
 * different environments.</p>
 */
public class ImportTransactionTemplateJpaRepositoryImpl extends BaseRepositoryImpl<ImportTransactionTemplate>
    implements ImportTransactionTemplateJpaRepositoryCustom {

  @Autowired
  private ImportTransactionPlatformJpaRepository importTransactionPlatformJpaRepository;

  @Autowired
  private ImportTransactionTemplateJpaRepository importTransactionTemplateJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Override
  public ImportTransactionTemplate saveOnlyAttributes(ImportTransactionTemplate importTransactionTemplate,
      ImportTransactionTemplate existingEntity, final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    TemplateConfiguration importTemplate = null;

    // Before a template is saved, it is checked against configuration errors
    if (importTransactionTemplate.getTemplateFormatType() == TemplateFormatType.PDF) {
      importTemplate = new TemplateConfigurationPDFasTXT(importTransactionTemplate, user.createAndGetJavaLocale());
    } else {
      List<TemplateIdPurposeCsv> templateIdPurposeCsvList = importTransactionTemplateJpaRepository
          .getTemplateIdPurposeCsv(importTransactionTemplate.getIdTransactionImportPlatform());
      importTemplate = new TemplateConfigurationAndStateCsv(importTransactionTemplate, user.createAndGetJavaLocale(),
          templateIdPurposeCsvList);
    }
    importTemplate.parseTemplateAndThrowError(true);

    return importTransactionTemplateJpaRepository.save(importTransactionTemplate);
  }

  @Override
  @Transactional
  public FormTemplateCheck checkFormAgainstTemplate(FormTemplateCheck formTemplateCheck, Locale userLocale)
      throws Exception {
    List<ImportTransactionTemplate> importTransactionTemplateList = importTransactionTemplateJpaRepository
        .findByIdTransactionImportPlatformAndTemplateFormatTypeOrderByTemplatePurpose(
            formTemplateCheck.getIdTransactionImportPlatform(), TemplateFormatType.PDF.getValue());

    Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap = ImportTransactionHelperPdf
        .readTemplates(importTransactionTemplateList, userLocale);
    ParseFormInputPDFasTXT parseInputPDFasTXT = new ParseFormInputPDFasTXT(formTemplateCheck.getPdfAsTxt(),
        templateScannedMap);
    List<ImportProperties> importPropertiesList = parseInputPDFasTXT.parseInput();

    if (importPropertiesList != null) {
      // Found matching template
      ImportTransactionPos importTransactionPos = ImportTransactionPos
          .createFromImportPropertiesSecurity(importPropertiesList);
      TransactionImportHelper.setSecurityToImportWhenPossible(importTransactionPos, securityJpaRepository);
      importTransactionPos.calcDiffCashaccountAmountWhenPossible();
      formTemplateCheck.setImportTransactionPos(importTransactionPos);
      ImportTransactionTemplate importTransactionTemplate = parseInputPDFasTXT.getSuccessTemplate(importPropertiesList);
      formTemplateCheck.setSuccessParsedTemplateState(new ParsedTemplateState(
          importTransactionTemplate.getTemplatePurpose(), importTransactionTemplate.getValidSince()));
    } else {
      // Found no matching template
      formTemplateCheck.setFailedParseTemplateStateList(parseInputPDFasTXT.getLastMatchingProperties());
    }
    return formTemplateCheck;
  }

  @Override
  public List<ValueKeyHtmlSelectOptions> getPossibleLanguagesForTemplate() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Locale userLocale = user.createAndGetJavaLocale();
    return Arrays.stream(Locale.getAvailableLocales()).filter(DataBusinessHelper.distinctByKey(Locale::getLanguage))
        .map(loc -> new ValueKeyHtmlSelectOptions(loc.getLanguage(), loc.getDisplayLanguage(userLocale)))
        .sorted((x, y) -> x.value.compareTo(y.value)).collect(Collectors.toList());
  }

  @Override
  public List<ValueKeyHtmlSelectOptions> getCSVTemplateIdsAsValueKeyHtmlSelectOptions(
      Integer idTransactionImportPlatform) {
    List<TemplateIdPurposeCsv> tpcList = importTransactionTemplateJpaRepository
        .getTemplateIdPurposeCsv(idTransactionImportPlatform);
    List<ValueKeyHtmlSelectOptions> vkhsoList = new ArrayList<>();
    tpcList.forEach(tpc -> vkhsoList.add(new ValueKeyHtmlSelectOptions(tpc.getIdTransactionImportTemplate().toString(),
        tpc.getTemplateId() + " - " + tpc.getTemplatePurpose())));
    return vkhsoList;
  }

  @Override
  public void getTemplatesByPlatformPlanAsZip(Integer idTransactionImportPlatform, HttpServletResponse response) {
    Optional<ImportTransactionPlatform> itpOpt = importTransactionPlatformJpaRepository
        .findById(idTransactionImportPlatform);
    if (itpOpt.isPresent()) {
      List<ImportTransactionTemplate> importTransactionTemplateList = importTransactionTemplateJpaRepository
          .findByIdTransactionImportPlatformOrderByTemplatePurpose(idTransactionImportPlatform);
      final DateFormat dateFormat = new SimpleDateFormat(GlobalConstants.SHORT_STANDARD_DATE_FORMAT);
      // setting headers
      response.setStatus(HttpServletResponse.SC_OK);
      response.addHeader("Content-Disposition", "attachment; filename=\"" + itpOpt.get().getName() + "\"");
      try {
        ZipOutputStream zipOutStream = new ZipOutputStream(response.getOutputStream());

        for (ImportTransactionTemplate itt : importTransactionTemplateList) {
          ZipEntry zipEntry = new ZipEntry(getTemplateFileName(itt, dateFormat));
          zipOutStream.putNextEntry(zipEntry);
          itt.replacePurposeInTemplateAsText();
          zipOutStream.write(itt.getTemplateAsTxt().getBytes());
          zipOutStream.closeEntry();
        }
        zipOutStream.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Generates a standardized filename for template export based on template metadata.
   * This method creates consistent, descriptive filenames that include all essential template
   * identification information, enabling easy recognition and sorting of template files
   * during export, backup, or deployment operations.
   * 
   * <p>The filename format follows the pattern:</p>
   * <code>{category}-{format}-{date}-{language}.tmpl</code>
   * 
   * <p>Where:</p>
   * <ul>
   *   <li><b>category:</b> Template category (accumulate, dividend, etc.) in lowercase</li>
   *   <li><b>format:</b> Template format type (csv, pdf) in lowercase</li>
   *   <li><b>date:</b> Valid since date in yyyy-MM-dd format</li>
   *   <li><b>language:</b> Template language code (en, de, fr, etc.)</li>
   *   <li><b>.tmpl:</b> Standard template file extension</li>
   * </ul>
   * 
   * @param itt The import transaction template to generate filename for
   * @param dateFormat Configured date formatter for consistent date representation
   * @return Standardized filename incorporating all template metadata
   */
  private String getTemplateFileName(ImportTransactionTemplate itt, DateFormat dateFormat) {
    return itt.getTemplateCategory().name().toLowerCase() + "-" + itt.getTemplateFormatType() + "-"
        + dateFormat.format(itt.getValidSince()) + "-" + itt.getTemplateLanguage() + ".tmpl";
  }

  @Override
  public SuccessFailedImportTransactionTemplate uploadImportTemplateFiles(Integer idTransactionImportPlatform,
      MultipartFile[] uploadFiles) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final DateFormat dateFormat = new SimpleDateFormat(GlobalConstants.SHORT_STANDARD_DATE_FORMAT);
    SuccessFailedImportTransactionTemplate sfitt = new SuccessFailedImportTransactionTemplate();
    for (MultipartFile uploadFile : uploadFiles) {
      String fileName = uploadFile.getOriginalFilename().replaceFirst("\\.tmpl$", "");
      String[] fileNameParts = fileName.split("-");
      ImportTransactionTemplate itt = new ImportTransactionTemplate(
          TemplateCategory.valueOf(fileNameParts[0].toUpperCase()),
          TemplateFormatType.valueOf(fileNameParts[1].toUpperCase()), fileNameParts[3].toLowerCase());
      try {
        itt.setValidSince(dateFormat.parse(fileNameParts[2]));
      } catch (ParseException e) {
        sfitt.fileNameError++;
        continue;
      }
      if (!itt.isLanguageLocaleOK()) {
        sfitt.fileNameError++;
        continue;
      }
      try {
        itt.setTemplateAsTxt(getTemplateAsText(uploadFile));
        if (!itt.copyPurposeInTextToFieldPurpose()) {
          sfitt.contentError++;
          continue;
        }
      } catch (IOException e) {
        sfitt.contentError++;
        continue;
      }
      saveImportedTemplate(user, idTransactionImportPlatform, itt, sfitt);
    }

    return sfitt;
  }

  /**
   * Saves an imported template with comprehensive validation, permission checking, and update handling.
   * This method manages the process of storing uploaded templates, including duplicate detection,
   * permission validation, and proper handling of both new template creation and existing template updates.
   * 
   * <p>The save process includes:</p>
   * <ul>
   *   <li><b>Duplicate Detection:</b> Checks for existing templates with identical metadata</li>
   *   <li><b>Permission Validation:</b> Ensures user has rights to create or modify templates</li>
   *   <li><b>Entity Management:</b> Handles both new creation and existing entity updates</li>
   *   <li><b>Statistics Tracking:</b> Updates result counters for comprehensive upload feedback</li>
   * </ul>
   * 
   * <p>Permission checking ensures users can only modify templates they own or have
   * administrative privileges for, preventing unauthorized template modifications.</p>
   * 
   * @param user The authenticated user performing the template import
   * @param idTransactionImportPlatform The platform ID to associate the template with
   * @param itt The template entity to save with all metadata populated
   * @param sfitt Statistics container to track save results (updated by reference)
   */
  private void saveImportedTemplate(final User user, final Integer idTransactionImportPlatform,
      ImportTransactionTemplate itt, SuccessFailedImportTransactionTemplate sfitt)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    ImportTransactionTemplate ittExisting = null;
    Optional<ImportTransactionTemplate> ittExistingOpt = importTransactionTemplateJpaRepository
        .findByIdTransactionImportPlatformAndTemplateCategoryAndTemplateFormatTypeAndValidSinceAndTemplateLanguage(
            idTransactionImportPlatform, itt.getTemplateCategory().getValue(), itt.getTemplateFormatType().getValue(),
            itt.getValidSince(), itt.getTemplateLanguage());
    if (ittExistingOpt.isPresent()) {
      ittExisting = ittExistingOpt.get();
      if (UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, ittExisting)) {
        DataHelper.updateEntityWithUpdatable(itt, ittExisting,
            Set.of(PropertySelectiveUpdatableOrWhenNull.class, PropertyAlwaysUpdatable.class));
        itt = ittExisting;
        sfitt.successUpdated++;
      } else {
        sfitt.notOwner++;
        return;
      }
    } else {
      itt.setIdTransactionImportPlatform(idTransactionImportPlatform);
      sfitt.successNew++;
    }
    saveOnlyAttributes(itt, ittExisting,
        Set.of(PropertySelectiveUpdatableOrWhenNull.class, PropertyAlwaysUpdatable.class));
  }

  /**
   * Extracts text content from uploaded template files with proper encoding handling.
   * This method safely reads the content of uploaded MultipartFile objects, ensuring
   * proper character encoding (UTF-8) and complete content extraction for template
   * processing and validation.
   * 
   * @param uploadFile The multipart file containing template content to extract
   * @return The complete text content of the uploaded file as UTF-8 encoded string, or null if file is empty
   * @throws IOException if file reading fails due to I/O errors or stream access issues
   */
  private String getTemplateAsText(MultipartFile uploadFile) throws IOException {
    String templateAsText = null;
    if (!uploadFile.isEmpty()) {
      InputStream initialStream = uploadFile.getInputStream();
      byte[] buffer = new byte[initialStream.available()];
      initialStream.read(buffer);
      templateAsText = new String(buffer, StandardCharsets.UTF_8);
    }
    return templateAsText;
  }

}
