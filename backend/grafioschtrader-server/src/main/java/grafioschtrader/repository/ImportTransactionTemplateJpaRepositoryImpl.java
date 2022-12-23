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

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.common.PropertySelectiveUpdatableOrWhenNull;
import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.User;
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
    return Arrays.stream(Locale.getAvailableLocales()).filter(DataHelper.distinctByKey(Locale::getLanguage))
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
