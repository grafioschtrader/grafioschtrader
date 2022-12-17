package grafioschtrader.repository;

import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platformimport.FormTemplateCheck;

public interface ImportTransactionTemplateJpaRepositoryCustom extends BaseRepositoryCustom<ImportTransactionTemplate> {
  FormTemplateCheck checkFormAgainstTemplate(FormTemplateCheck formTemplateCheck, Locale userLocale) throws Exception;

  List<ValueKeyHtmlSelectOptions> getPossibleLanguagesForTemplate();

  List<ValueKeyHtmlSelectOptions> getCSVTemplateIdsAsValueKeyHtmlSelectOptions(Integer idTransactionImportPlatform);

  void getTemplatesByPlatformPlanAsZip(Integer idTransactionImportPlatform, HttpServletResponse response);

  SuccessFailedImportTransactionTemplate uploadImportTemplateFiles(Integer idTransactionImportPlatform,
      MultipartFile[] uploadFiles) throws Exception;

  public static class SuccessFailedImportTransactionTemplate {
    public int successNew;
    public int successUpdated;
    public int notOwner;
    public int fileNameError;
    public int contentError;
  }
}
