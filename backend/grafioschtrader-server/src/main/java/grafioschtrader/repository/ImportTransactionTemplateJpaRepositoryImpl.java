package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.common.DataHelper;
import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.User;
import grafioschtrader.platform.TransactionImportHelper;
import grafioschtrader.platformimport.FormTemplateCheck;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.platformimport.ParsedTemplateState;
import grafioschtrader.platformimport.TemplateConfiguration;
import grafioschtrader.platformimport.csv.TemplateConfigurationAndStateCsv;
import grafioschtrader.platformimport.pdf.ImportTransactionHelperPdf;
import grafioschtrader.platformimport.pdf.ParseFormInputPDFasTXT;
import grafioschtrader.platformimport.pdf.TemplateConfigurationPDFasTXT;
import grafioschtrader.types.TemplateFormatType;

public class ImportTransactionTemplateJpaRepositoryImpl extends BaseRepositoryImpl<ImportTransactionTemplate>
    implements ImportTransactionTemplateJpaRepositoryCustom {

  @Autowired
  ImportTransactionTemplateJpaRepository importTransactionTemplateJpaRepository;

  @Autowired
  SecurityJpaRepository securityJpaRepository;

  @Override
  public ImportTransactionTemplate saveOnlyAttributes(ImportTransactionTemplate importTransactionTemplate,
      ImportTransactionTemplate existingEntity, final Set<Class<? extends Annotation>> udatePropertyLevelClasses) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    TemplateConfiguration importTemplate = null;

    // Before a template is saved, it is checked against configuration errors
    if (importTransactionTemplate.getTemplateFormatType() == TemplateFormatType.PDF) {
      importTemplate = new TemplateConfigurationPDFasTXT(importTransactionTemplate, user.createAndGetJavaLocale());
    } else {
      importTemplate = new TemplateConfigurationAndStateCsv(importTransactionTemplate, user.createAndGetJavaLocale());
    }
    importTemplate.parseTemplate();

    return importTransactionTemplateJpaRepository.save(importTransactionTemplate);
  }

  @Override
  @Transactional
  public FormTemplateCheck checkFormAgainstTemplate(FormTemplateCheck formTemplateCheck, Locale userLocale) throws Exception {
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
      importTransactionPos.calcDiffCashaccountAmount();
      formTemplateCheck.setImportTransactionPos(importTransactionPos);
      ImportTransactionTemplate importTransactionTemplate = parseInputPDFasTXT.getSuccessTemplate(importPropertiesList);
      formTemplateCheck
          .setSuccessParsedTemplateState(new ParsedTemplateState(importTransactionTemplate.getTemplatePurpose(),
              importTransactionTemplate.getValidSince()));
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
        .sorted((x, y) -> x.value.compareTo(y.value))
        .collect(Collectors.toList());
  }

}
