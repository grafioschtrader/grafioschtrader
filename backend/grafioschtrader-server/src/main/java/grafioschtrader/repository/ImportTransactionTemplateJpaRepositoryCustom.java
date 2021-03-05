package grafioschtrader.repository;

import java.util.List;
import java.util.Locale;

import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platformimport.FormTemplateCheck;

public interface ImportTransactionTemplateJpaRepositoryCustom extends BaseRepositoryCustom<ImportTransactionTemplate> {
  FormTemplateCheck checkFormAgainstTemplate(FormTemplateCheck formTemplateCheck, Locale userLocale) throws Exception;

  List<ValueKeyHtmlSelectOptions> getPossibleLanguagesForTemplate();
}
