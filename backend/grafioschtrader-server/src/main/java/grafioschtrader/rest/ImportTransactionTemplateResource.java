package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.entities.User;
import grafioschtrader.platformimport.FormTemplateCheck;
import grafioschtrader.repository.ImportTransactionTemplateJpaRepository;
import grafioschtrader.repository.ImportTransactionTemplateJpaRepositoryCustom.SuccessFailedImportTransactionTemplate;
import grafioschtrader.repository.TradingPlatformPlanJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping(RequestMappings.IMPORTTRANSACTIONTEMPLATE_MAP)
@Tag(name = RequestMappings.IMPORTTRANSACTIONTEMPLATE, description = "Controller for Import Transaction Tempalte")
public class ImportTransactionTemplateResource extends UpdateCreateDeleteAuditResource<ImportTransactionTemplate> {

  @Autowired
  private ImportTransactionTemplateJpaRepository importTransactionTemplateJpaRepository;

  @Autowired
  private TradingPlatformPlanJpaRepository tradingPlatformPlanJpaRepository;

  @Operation(summary = "Return all import transaction template for a certain import transaction platform", description = "", tags = {
      RequestMappings.IMPORTTRANSACTIONTEMPLATE })
  @GetMapping(value = "/importtransactionplatform/{idTransactionImportPlatform}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionTemplate>> getImportTransactionPlatformByPlatform(
      @Parameter(description = "ID of the import transaction platform", required = true) @PathVariable final Integer idTransactionImportPlatform,
      @Parameter(description = "true when a empty template is requried", required = true) @RequestParam() final boolean excludeTemplate) {

    return getImportTransactionPlatformById(idTransactionImportPlatform, excludeTemplate);
  }

  @Operation(summary = "Return all import transaction template for a certain trading platform plan", description = "", tags = {
      RequestMappings.IMPORTTRANSACTIONTEMPLATE })
  @GetMapping(value = "/importtransactionplatform/tradingplatformplan/{idTradingPlatformPlan}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionTemplate>> getImportTransactionPlatformByTradingPlatformPlan(
      @Parameter(description = "Id of trading platform plan", required = true) @PathVariable final Integer idTradingPlatformPlan,
      @Parameter(description = "true when a empty template is requried", required = true) @RequestParam() final boolean excludeTemplate) {
    TradingPlatformPlan tradingPlatformPlan = tradingPlatformPlanJpaRepository.findById(idTradingPlatformPlan)
        .orElse(null);
    return getImportTransactionPlatformById(
        tradingPlatformPlan.getImportTransactionPlatform().getIdTransactionImportPlatform(), excludeTemplate);
  }

  @Operation(summary = "Return all CSV of certain trading platform plan with its templateId", description = "Can be used as options for a html select", tags = {
      RequestMappings.IMPORTTRANSACTIONTEMPLATE })
  @GetMapping(value = "/importtransactionplatform/tradingplatformplan/csv/{idTradingPlatformPlan}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ValueKeyHtmlSelectOptions>> getCSVTemplateIdsAsValueKeyHtmlSelectOptions(
      @Parameter(description = "Id of trading platform plan", required = true) @PathVariable final Integer idTradingPlatformPlan) {
    return new ResponseEntity<>(
        importTransactionTemplateJpaRepository.getCSVTemplateIdsAsValueKeyHtmlSelectOptions(idTradingPlatformPlan),
        HttpStatus.OK);
  }

  private ResponseEntity<List<ImportTransactionTemplate>> getImportTransactionPlatformById(
      Integer idTransactionImportPlatform, boolean excludeTemplate) {
    List<ImportTransactionTemplate> importTransactionTemplateList = importTransactionTemplateJpaRepository
        .findByIdTransactionImportPlatformOrderByTemplatePurpose(idTransactionImportPlatform);
    if (excludeTemplate) {
      importTransactionTemplateList
          .forEach(importTransactionTemplate -> importTransactionTemplate.setTemplateAsTxt(null));
    }
    return new ResponseEntity<>(importTransactionTemplateList, HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<ImportTransactionTemplate> getUpdateCreateJpaRepository() {
    return importTransactionTemplateJpaRepository;
  }

  @Operation(summary = "Check a text of a transaction report against the form temlates to match the input fields", description = "", tags = {
      RequestMappings.IMPORTTRANSACTIONTEMPLATE })
  @PostMapping(value = "/checkformagainsttemplate")
  public ResponseEntity<FormTemplateCheck> checkFormAgainstTemplate(@RequestBody FormTemplateCheck formTemplateCheck)
      throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(importTransactionTemplateJpaRepository.checkFormAgainstTemplate(formTemplateCheck,
        user.createAndGetJavaLocale()), HttpStatus.OK);
  }

  @Operation(summary = "Returns the possible languages for the template", description = "", tags = {
      RequestMappings.IMPORTTRANSACTIONTEMPLATE })
  @GetMapping(value = "/languages", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ValueKeyHtmlSelectOptions>> getPossibleLanguagesForTemplate() {
    return new ResponseEntity<>(importTransactionTemplateJpaRepository.getPossibleLanguagesForTemplate(),
        HttpStatus.OK);
  }

  @Operation(summary = "Export all import transaction template of a certain trading platform plan", description = "", tags = {
      RequestMappings.IMPORTTRANSACTIONTEMPLATE })
  @GetMapping(value = "/exportalltemplates/{idTransactionImportPlatform}", produces = "application/zip")
  public void getTemplatesByPlatformPlanAsZip(
      @Parameter(description = "ID of the import transaction platform", required = true) @PathVariable final Integer idTransactionImportPlatform,
      HttpServletResponse response) throws Exception {
    importTransactionTemplateJpaRepository.getTemplatesByPlatformPlanAsZip(idTransactionImportPlatform, response);
  }

  @Operation(summary = "Upload one or more Template files, each Import transaction template", description = "", tags = {
      RequestMappings.IMPORTTRANSACTIONTEMPLATE })
  @PostMapping(value = "uploadtemplatefiles/{idTransactionImportPlatform}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SuccessFailedImportTransactionTemplate> uploadImportTemplateFiles(
      @PathVariable() Integer idTransactionImportPlatform, @RequestParam("file") MultipartFile[] uploadFiles)
      throws Exception {
    return new ResponseEntity<>(
        importTransactionTemplateJpaRepository.uploadImportTemplateFiles(idTransactionImportPlatform, uploadFiles),
        HttpStatus.OK);
  }

}
